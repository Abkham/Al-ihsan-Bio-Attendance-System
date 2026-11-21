import { BACKEND_BASE_URL, getErrorMessage } from "./api";
import axios from "axios";
import { getEmployeeBiometrics, registerBiometric } from "./biometricApi";

/*
  BIOMETRIC VERIFICATION (REAL, SERVER-VERIFIED WEBAUTHN) WITH FALLBACK
  -----------------------------------------------------------------------
  PRIMARY  : the device's own platform authenticator - fingerprint, Face
             unlock, Windows Hello, device PIN, or phone screen-lock -
             through the browser's native WebAuthn API, verified
             CRYPTOGRAPHICALLY on the server by Spring Security WebAuthn
             (spring-security-webauthn). The server never trusts a bare
             credential id as proof of identity: every registration and
             every sign-in is checked against the stored public key,
             origin, RP ID, challenge and signature.
  NO FALLBACK: attendance is recorded ONLY after a real fingerprint / Face /
             screen-lock check with the registered device. There is no password
             option, and the server also refuses check-in / check-out without a
             fresh fingerprint proof (see FingerprintProof on the backend).

  How it works now
  1. Once, in Setting > Attendance Device ("Register this device"): the browser
     asks Spring Security for real registration options (POST /webauthn/register/options - requires the
     employee to already have an authenticated session from the normal
     username/password login), creates a credential on the device
     (fingerprint/Face/PIN prompt), and posts the signed attestation back
     to the server (POST /webauthn/register). Spring Security verifies it
     and stores the credential's PUBLIC KEY server-side, in the
     `user_credentials` table - never a fingerprint image or template.
  2. Next times: the browser asks the server for a real authentication
     challenge (POST /webauthn/authenticate/options), the device signs it
     with the SAME private key (fingerprint/Face/PIN prompt required
     again), and the signed assertion is posted to POST /login/webauthn,
     where Spring Security verifies the cryptographic signature against
     the stored public key before accepting it.

  The `Biometric` / `/api/biometrics` table is kept ONLY as a lightweight,
  UI-facing mirror (so the dashboard can show "this device is
  registered"). It is written to AFTER a real WebAuthn ceremony already
  succeeded, and it is never treated as proof of identity by itself.
*/

// ---------- dedicated client for the root-level /webauthn and /login/webauthn endpoints ----------
// (these are NOT under /api - they are Spring Security's own endpoints)

const webauthnClient = axios.create({
  baseURL: BACKEND_BASE_URL,
  timeout: 20000,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
    "X-Requested-With": "XMLHttpRequest",
  },
});

// ---------- small helpers ----------

const bufferToBase64url = (buffer) => {
  const bytes = new Uint8Array(buffer);
  let binary = "";

  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });

  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
};

const base64urlToBuffer = (value) => {
  const base64 =
    value.replace(/-/g, "+").replace(/_/g, "/") +
    "=".repeat((4 - (value.length % 4)) % 4);

  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);

  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }

  return bytes.buffer;
};

const getEmployeeKey = (employee) =>
  String(employee?.employeeId ?? "").trim();

const detectOs = (ua) => {
  if (/Windows/i.test(ua)) return "Windows";
  if (/Android/i.test(ua)) return "Android";
  if (/iPhone|iPad|iPod/i.test(ua)) return "iOS";
  if (/Mac OS X/i.test(ua)) return "macOS";
  if (/CrOS/i.test(ua)) return "ChromeOS";
  if (/Linux/i.test(ua)) return "Linux";
  return "";
};

const detectBrowser = (ua) => {
  if (/Edg\//i.test(ua)) return "Edge";
  if (/OPR\//i.test(ua)) return "Opera";
  if (/SamsungBrowser/i.test(ua)) return "Samsung Internet";
  if (/Firefox\//i.test(ua)) return "Firefox";
  if (/Chrome\//i.test(ua)) return "Chrome";
  if (/Safari\//i.test(ua)) return "Safari";
  return "";
};

/*
  What we can honestly learn about THIS device from the browser.

  - Browsers do NOT reveal the exact phone/PC model to web pages in general.
  - Chromium on Android can reveal it (User-Agent Client Hints "model") - e.g. "SM-A065F".
  - Safari/iOS, Firefox and desktop browsers do not, so for them we only know the
    operating system and browser, and the employee can type a nickname.
  - Everything returned here is INFORMATION ONLY. It is never used for security: the
    real proof of the device is the WebAuthn credential created on it.
*/
export const getDeviceInfo = async () => {
  const ua = navigator.userAgent || "";

  let platform = detectOs(ua);
  let browser = detectBrowser(ua);
  let deviceModel = "";

  const uaData = navigator.userAgentData;

  if (uaData) {
    try {
      const hints = await uaData.getHighEntropyValues(["model", "platform"]);

      if (hints?.model) deviceModel = String(hints.model).trim();
      if (hints?.platform) platform = String(hints.platform).trim() || platform;

      const brand = (uaData.brands || []).find(
        (b) => b.brand && !/not.?a.?brand|chromium/i.test(b.brand)
      );

      if (brand && !browser) browser = brand.brand;
    } catch {
      /* hints refused - keep what we have */
    }
  }

  return { deviceModel, platform, browser };
};

// Text shown for a device when the employee did not type a nickname.
export const describeDevice = ({ deviceNickname, deviceModel, platform, browser }) => {
  if (deviceNickname) return deviceNickname;

  if (deviceModel) return browser ? `${deviceModel} (${browser})` : deviceModel;

  const base = platform || "Device";

  return browser ? `${base} - ${browser}` : base;
};

// Kept for older callers: generic "Windows - Chrome" style label.
export const getDeviceName = () => {
  const ua = navigator.userAgent || "";
  return describeDevice({
    platform: detectOs(ua),
    browser: detectBrowser(ua),
  });
};

// ---------- capability check ----------

export const checkBiometricSupport = async () => {
  if (!window.isSecureContext) {
    return {
      supported: false,
      reason:
        "Biometric verification needs a secure connection (HTTPS or localhost) on this device.",
    };
  }

  if (!window.PublicKeyCredential || !navigator.credentials) {
    return {
      supported: false,
      reason: "This browser does not support biometric (WebAuthn) verification.",
    };
  }

  try {
    const available =
      await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();

    if (!available) {
      return {
        supported: false,
        reason:
          "No fingerprint, Face unlock, Windows Hello or screen lock is set up on this device.",
      };
    }
  } catch {
    return {
      supported: false,
      reason: "Biometric hardware could not be detected on this device.",
    };
  }

  return { supported: true, reason: "" };
};

// ---------- decode server -> browser (base64url strings -> ArrayBuffers) ----------

const decodeCreationOptions = (options) => ({
  ...options,
  challenge: base64urlToBuffer(options.challenge),
  user: {
    ...options.user,
    id: base64urlToBuffer(options.user.id),
  },
  excludeCredentials: (options.excludeCredentials || []).map((descriptor) => ({
    ...descriptor,
    id: base64urlToBuffer(descriptor.id),
  })),
});

const decodeRequestOptions = (options) => ({
  ...options,
  challenge: base64urlToBuffer(options.challenge),
  allowCredentials: (options.allowCredentials || []).map((descriptor) => ({
    ...descriptor,
    id: base64urlToBuffer(descriptor.id),
  })),
});

// ---------- encode browser -> server (ArrayBuffers -> base64url strings) ----------

const encodeAttestationCredential = (credential) => {
  const response = credential.response;

  return {
    id: credential.id,
    rawId: bufferToBase64url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment || null,
    clientExtensionResults: credential.getClientExtensionResults
      ? credential.getClientExtensionResults()
      : {},
    response: {
      clientDataJSON: bufferToBase64url(response.clientDataJSON),
      attestationObject: bufferToBase64url(response.attestationObject),
      transports:
        typeof response.getTransports === "function"
          ? response.getTransports()
          : [],
    },
  };
};

const encodeAssertionCredential = (credential) => {
  const response = credential.response;

  return {
    id: credential.id,
    rawId: bufferToBase64url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment || null,
    clientExtensionResults: credential.getClientExtensionResults
      ? credential.getClientExtensionResults()
      : {},
    response: {
      clientDataJSON: bufferToBase64url(response.clientDataJSON),
      authenticatorData: bufferToBase64url(response.authenticatorData),
      signature: bufferToBase64url(response.signature),
      userHandle: response.userHandle
        ? bufferToBase64url(response.userHandle)
        : null,
    },
  };
};

// ---------- real Spring Security WebAuthn ceremony ----------

/*
  Registers a brand-new platform credential for the CURRENTLY LOGGED-IN
  employee (their normal username/password session must still be active -
  Spring Security needs to know who is registering).

  1. POST /webauthn/register/options  -> server-generated challenge + RP info
  2. navigator.credentials.create()   -> triggers Windows Hello / fingerprint / etc.
  3. POST /webauthn/register          -> server verifies + stores the public key
*/
const registerRealCredential = async (employee, nickname = "") => {
  const info = await getDeviceInfo();
  const details = { ...info, deviceNickname: (nickname || "").trim() };
  const deviceLabel = describeDevice(details);

  // The server refuses this call (409) when the account already has a device.
  const { data: creationOptions } = await webauthnClient.post(
    "/webauthn/register/options",
    {}
  );

  const publicKey = decodeCreationOptions(creationOptions);

  const credential = await navigator.credentials.create({ publicKey });

  if (!credential) {
    throw new Error("Biometric registration was not completed.");
  }

  const credentialJSON = encodeAttestationCredential(credential);

  await webauthnClient.post("/webauthn/register", {
    publicKey: {
      credential: credentialJSON,
      label: deviceLabel,
    },
  });

  // Friendly record for the lists (name, nickname, date). The server checks that this
  // credential really exists for THIS account before storing it.
  await registerBiometric(getEmployeeKey(employee), {
    fingerprintId: credentialJSON.id,
    ...details,
  }).catch((error) => {
    if (error?.response?.status === 409) throw error;
    console.warn("Could not save the device details:", error);
  });

  return credentialJSON.id;
};

/*
  Proves possession of a PREVIOUSLY REGISTERED platform credential and, on
  success, has Spring Security establish a fresh authenticated session for
  the resolved employee.

  1. POST /webauthn/authenticate/options -> server-generated challenge
     (scoped to the currently authenticated employee's own credentials,
     since their password-login session is still active)
  2. navigator.credentials.get()         -> triggers Windows Hello / fingerprint / etc.
  3. POST /login/webauthn                -> server verifies the signature
     against the stored public key
*/
const authenticateRealCredential = async () => {
  const { data: requestOptions } = await webauthnClient.post(
    "/webauthn/authenticate/options",
    {}
  );

  const publicKey = decodeRequestOptions(requestOptions);

  const assertion = await navigator.credentials.get({ publicKey });

  if (!assertion) {
    throw new Error("Biometric verification was not completed.");
  }

  const assertionJSON = encodeAssertionCredential(assertion);

  await webauthnClient.post("/login/webauthn", assertionJSON);

  return assertionJSON.id;
};

/*
  Public entry point for the "Register this device" button in the
  employee Settings page. Requires the employee to already be logged in
  (normal username/password session). Throws a humanized Error on
  failure/cancellation so the caller can show it directly.
*/
export const registerDeviceCredential = async (employee, nickname = "") => {
  try {
    return await registerRealCredential(employee, nickname);
  } catch (error) {
    console.warn("Manual device registration failed:", error);
    throw new Error(humanizeWebAuthnError(error));
  }
};

/*
  Removing a device is done ONLY by the administrator (Manage Employee > Reset device).
  The old direct DELETE /webauthn/register/{id} call is refused by the server on purpose.
*/

const humanizeWebAuthnError = (error) => {
  if (error?.name === "NotAllowedError") {
    return `Biometric verification was cancelled, timed out, or was denied on this device. [Technical: ${error.name} - ${error.message}] [Page: ${window.location.origin}]`;
  }

  if (error?.name === "InvalidStateError") {
    return "This device already has a biometric credential registered for this account.";
  }

  if (error?.name === "SecurityError") {
    return `Biometric sign-in is not allowed from this page address. [Technical: ${error.name} - ${error.message}] [Page: ${window.location.origin}]`;
  }

  if (error?.response) {
    return getErrorMessage(
      error,
      "The server could not verify your biometric credential."
    );
  }

  return (
    error?.message ||
    "Fingerprint / biometric verification failed, was cancelled, or this device is not registered yet."
  );
};

// ---------- MAIN ENTRY ----------

/*
  Returns:
    { verified: true,  method: "BIOMETRIC" }
    { verified: false, message }

  Attendance is recorded only when the fingerprint check of the registered device succeeds.
  A device is registered once, on the Setting page - never here and never with a password.
*/
export const verifyIdentity = async (employee) => {
  const employeeId = getEmployeeKey(employee);

  const support = await checkBiometricSupport();

  if (!support.supported) {
    return {
      verified: false,
      message: `${support.reason}\n\nAttendance can only be recorded with the fingerprint of your registered device. Attendance has NOT been recorded.`,
    };
  }

  // The server keeps this list in step with the real stored credential.
  try {
    const devices = await getEmployeeBiometrics(employeeId);

    const hasDevice = (Array.isArray(devices) ? devices : []).some(
      (item) => item.status === "Active"
    );

    if (!hasDevice) {
      return {
        verified: false,
        message:
          "No device is registered on your account yet.\n\nOpen Setting > Attendance Device and press \"Register this device\" first. Attendance has NOT been recorded.",
      };
    }
  } catch (error) {
    // Do not block on this lookup: the server still decides during the fingerprint check.
    console.warn("Could not load the registered device:", error);
  }

  try {
    await authenticateRealCredential();

    return { verified: true, method: "BIOMETRIC" };
  } catch (error) {
    console.warn("Biometric verification failed:", error);

    return {
      verified: false,
      message: `${humanizeWebAuthnError(error)}\n\nFingerprint verification with your registered device is required. Attendance has NOT been recorded.`,
    };
  }
};

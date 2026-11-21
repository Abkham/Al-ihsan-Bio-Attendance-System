import React, { useCallback, useEffect, useState } from "react";
import {
  checkBiometricSupport,
  registerDeviceCredential,
  verifyIdentity,
} from "./api/webauthn";
import { getEmployeeBiometrics } from "./api/biometricApi";
import {
  submitRemovalRequest,
  getMyRemovalRequests,
  cancelRemovalRequest,
  getMyNotifications,
  markNotificationsRead,
  getRecoveryStatus,
  verifyRecoveryCode,
} from "./api/deviceRequestApi";
import { getErrorMessage } from "./api/api";

/*
  Employee Settings -> "Attendance Device".

  ONE ACTIVE DEVICE PER ACCOUNT (enforced by the server):
  - the employee types a Device Name and registers the phone/computer used for attendance;
  - the employee CANNOT delete the device. They can only ASK the administrator:
      * "Request device removal" (new phone, damaged, biometric problems): needs a fresh
        fingerprint check on the registered device, then waits for the administrator's decision;
      * "My phone was stolen / lost": an emergency report the administrator can act on at once.
    After an emergency revocation the administrator confirms the employee's identity and gives a
    verification code, which must be entered here before a new device can be registered.
  - decisions arrive as notifications.
*/

const NORMAL_REASONS = [
  { value: "NEW_PHONE", label: "I bought a new phone" },
  { value: "DAMAGED_DEVICE", label: "My device is damaged (I can still log in)" },
  { value: "BIOMETRIC_PROBLEM", label: "Fingerprint / biometric problems" },
  { value: "OTHER", label: "Other reason (please explain)" },
];

const EMERGENCY_REASONS = [
  { value: "STOLEN", label: "My phone has been stolen" },
  { value: "LOST", label: "My phone is lost" },
];

const REASON_LABELS = Object.fromEntries(
  [...NORMAL_REASONS, ...EMERGENCY_REASONS].map((r) => [r.value, r.label])
);

function formatDate(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString();
}

function formatDateTime(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleString();
}

function BiometricSettings({ employee }) {
  const employeeId = String(employee?.employeeId ?? "").trim();

  const [supported, setSupported] = useState(true);
  const [supportReason, setSupportReason] = useState("");

  const [devices, setDevices] = useState([]);
  const [requests, setRequests] = useState([]);
  const [notes, setNotes] = useState({ unread: 0, items: [] });
  const [recovery, setRecovery] = useState({ required: false, verified: false });

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState({ type: "", text: "" });

  const [deviceName, setDeviceName] = useState("");
  const [recoveryCode, setRecoveryCode] = useState("");

  // "normal" | "emergency" | ""
  const [form, setForm] = useState("");
  const [reason, setReason] = useState("");
  const [description, setDescription] = useState("");

  const active = devices.find((item) => item.status === "Active") || null;
  const history = devices.filter((item) => item.status === "Revoked");
  const hasOpenNormal = requests.some(
    (r) => r.status === "PENDING" && r.requestType === "NORMAL"
  );
  const hasOpenEmergency = requests.some(
    (r) => r.status === "PENDING" && r.requestType === "EMERGENCY"
  );

  const say = (type, text) => setMessage({ type, text });

  const loadAll = useCallback(async () => {
    if (!employeeId) {
      setLoading(false);
      return;
    }

    try {
      const [list, mine, inbox, rec] = await Promise.all([
        getEmployeeBiometrics(employeeId),
        getMyRemovalRequests(employeeId),
        getMyNotifications(employeeId),
        getRecoveryStatus(employeeId),
      ]);

      setDevices(Array.isArray(list) ? list : []);
      setRequests(Array.isArray(mine) ? mine : []);
      setNotes({
        unread: Number(inbox?.unread ?? 0),
        items: Array.isArray(inbox?.items) ? inbox.items : [],
      });
      setRecovery({ required: Boolean(rec?.required), verified: Boolean(rec?.verified) });
    } catch (error) {
      console.error("Could not load the device information:", error);
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => {
    let cancelled = false;

    checkBiometricSupport().then((result) => {
      if (cancelled) return;
      setSupported(result.supported);
      setSupportReason(result.reason);
    });

    loadAll();

    return () => {
      cancelled = true;
    };
  }, [loadAll]);

  // ---------------- register ----------------

  const handleRegister = async (e) => {
    e.preventDefault();
    setMessage({ type: "", text: "" });

    const name = deviceName.trim();

    if (name.length < 2) {
      say("error", "Please enter a Device Name first (for example: My Tecno Phone).");
      return;
    }

    try {
      setBusy(true);

      await registerDeviceCredential(employee, name);

      setDeviceName("");
      say(
        "success",
        "This device is now registered. You can use its fingerprint / Face / screen lock to verify attendance."
      );
    } catch (error) {
      say("error", error?.message || "Biometric registration failed or was cancelled.");
    } finally {
      setBusy(false);
      await loadAll();
    }
  };

  const handleVerifyRecovery = async (e) => {
    e.preventDefault();
    setMessage({ type: "", text: "" });

    if (!recoveryCode.trim()) {
      say("error", "Please enter the verification code from the administrator.");
      return;
    }

    try {
      setBusy(true);
      const result = await verifyRecoveryCode(employeeId, recoveryCode.trim());
      setRecoveryCode("");
      say("success", result?.message || "Identity verified.");
    } catch (error) {
      say("error", getErrorMessage(error, "The code could not be verified."));
    } finally {
      setBusy(false);
      await loadAll();
    }
  };

  // ---------------- removal requests ----------------

  const openForm = (kind) => {
    setMessage({ type: "", text: "" });
    setReason("");
    setDescription("");
    setForm(kind);
  };

  const handleSubmitRequest = async (e) => {
    e.preventDefault();
    setMessage({ type: "", text: "" });

    const emergency = form === "emergency";

    if (!reason) {
      say("error", "Please choose a reason from the list.");
      return;
    }

    if (reason === "OTHER" && description.trim().length < 5) {
      say("error", "Please explain your reason.");
      return;
    }

    try {
      setBusy(true);

      if (!emergency) {
        // Extra identity check: only the owner of the registered device can ask to remove it.
        const check = await verifyIdentity(employee);

        if (!check.verified) {
          say("error", check.message || "Fingerprint verification failed. The request was NOT sent.");
          return;
        }
      }

      await submitRemovalRequest(employeeId, {
        requestType: emergency ? "EMERGENCY" : "NORMAL",
        reason,
        description: description.trim(),
      });

      setForm("");
      say(
        "success",
        emergency
          ? "Your emergency report was sent to the administrator. Please also contact the administrator directly so they can confirm your identity."
          : "Your request was sent. Its status is PENDING until the administrator decides."
      );
    } catch (error) {
      say("error", getErrorMessage(error, "The request could not be sent."));
    } finally {
      setBusy(false);
      await loadAll();
    }
  };

  const handleCancel = async (requestId) => {
    try {
      setBusy(true);
      await cancelRemovalRequest(employeeId, requestId);
      say("success", "The request was cancelled.");
    } catch (error) {
      say("error", getErrorMessage(error, "The request could not be cancelled."));
    } finally {
      setBusy(false);
      await loadAll();
    }
  };

  const handleReadAll = async () => {
    try {
      await markNotificationsRead(employeeId);
    } catch (error) {
      console.warn("Could not mark notifications as read:", error);
    }
    await loadAll();
  };

  const needsRecoveryCode = recovery.required && !recovery.verified;

  return (
    <div className="device-card">
      <h2>Attendance Device</h2>

      <p className="device-intro">
        Each account can have <strong>one</strong> registered device. Use its
        fingerprint, Face unlock, Windows Hello or screen lock to verify your
        attendance. Your fingerprint image is never stored - only a
        cryptographic key that stays on your device.
      </p>

      {message.text && (
        <div className={`device-message ${message.type}`} role="status">
          {message.text}
        </div>
      )}

      {!supported && <div className="device-message warning">{supportReason}</div>}

      {notes.items.length > 0 && (
        <div className="device-notes">
          <div className="device-notes-head">
            <strong>Notifications{notes.unread > 0 ? ` (${notes.unread} new)` : ""}</strong>
            {notes.unread > 0 && (
              <button type="button" onClick={handleReadAll}>
                Mark all as read
              </button>
            )}
          </div>
          <ul>
            {notes.items.slice(0, 6).map((n) => (
              <li key={n.id} className={n.read ? "" : "unread"}>
                <strong>{n.title}</strong>
                <span>{n.message}</span>
                <small>{formatDateTime(n.createdAt)}</small>
              </li>
            ))}
          </ul>
        </div>
      )}

      {loading ? (
        <p className="device-muted">Loading...</p>
      ) : active ? (
        <div className="device-active">
          <div>
            <div className="device-label">Registered device</div>

            <strong className="device-name">
              {active.deviceName || active.fingerName || active.displayName || "Device"}
            </strong>

            <dl className="device-details">
              <dt>Status</dt>
              <dd>
                <span className="device-status active">ACTIVE</span>
              </dd>
              <dt>Authentication</dt>
              <dd>{active.authenticationMethod || "Fingerprint (WebAuthn)"}</dd>
              {(active.platform || active.browser) && (
                <>
                  <dt>Browser / OS</dt>
                  <dd>
                    {[active.browser, active.platform].filter(Boolean).join(" · ")}
                  </dd>
                </>
              )}
              <dt>Registered</dt>
              <dd>{formatDate(active.registeredAt) || "-"}</dd>
              <dt>Last used</dt>
              <dd>{formatDateTime(active.lastUsedAt) || "Not used yet"}</dd>
            </dl>
          </div>
        </div>
      ) : needsRecoveryCode ? (
        <form className="device-register" onSubmit={handleVerifyRecovery}>
          <p className="device-muted">
            Your old device was revoked after your report. Before you can register a new
            one, the administrator must confirm your identity and give you a
            <strong> verification code</strong>.
          </p>

          <label htmlFor="recovery-code">Verification code</label>
          <input
            id="recovery-code"
            type="text"
            autoComplete="off"
            placeholder="XXXXX-XXXXX"
            value={recoveryCode}
            onChange={(e) => setRecoveryCode(e.target.value)}
          />

          <button type="submit" className="device-primary" disabled={busy}>
            {busy ? "Checking..." : "Verify my identity"}
          </button>
        </form>
      ) : (
        <form className="device-register" onSubmit={handleRegister}>
          <p className="device-muted">No device is registered on this account yet.</p>

          <label htmlFor="device-name">Device Name (required)</label>
          <input
            id="device-name"
            type="text"
            maxLength={40}
            autoComplete="off"
            placeholder="Example: My Tecno Phone, Office Laptop"
            value={deviceName}
            onChange={(e) => setDeviceName(e.target.value)}
          />

          <button
            type="submit"
            className="device-primary"
            disabled={!supported || busy}
          >
            {busy ? "Registering..." : "🔐 Register this device"}
          </button>
        </form>
      )}

      {active && (
        <div className="device-actions">
          <button
            type="button"
            onClick={() => openForm("normal")}
            disabled={busy || hasOpenNormal}
          >
            {hasOpenNormal ? "Removal request pending" : "Request device removal"}
          </button>

          <button
            type="button"
            className="danger"
            onClick={() => openForm("emergency")}
            disabled={busy || hasOpenEmergency}
          >
            {hasOpenEmergency ? "Emergency report sent" : "My phone was stolen / lost"}
          </button>
        </div>
      )}

      {form && active && (
        <form className="device-request-form" onSubmit={handleSubmitRequest}>
          <h3>
            {form === "emergency" ? "Report a stolen or lost phone" : "Request device removal"}
          </h3>

          {form === "normal" ? (
            <p className="device-muted">
              The administrator decides. To confirm it is really you, your registered
              device will ask for your fingerprint when you press Submit.
            </p>
          ) : (
            <p className="device-muted">
              The administrator is notified at once and can revoke the phone immediately.
              To register a new device later you must be identified by the administrator.
            </p>
          )}

          <label htmlFor="req-reason">Reason</label>
          <select
            id="req-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          >
            <option value="">Choose a reason...</option>
            {(form === "emergency" ? EMERGENCY_REASONS : NORMAL_REASONS).map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>

          <label htmlFor="req-desc">
            Explanation {reason === "OTHER" ? "(required)" : "(optional)"}
          </label>
          <textarea
            id="req-desc"
            rows={3}
            maxLength={500}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <div className="device-actions">
            <button type="submit" className="device-primary" disabled={busy}>
              {busy ? "Please wait..." : "Submit"}
            </button>
            <button type="button" onClick={() => setForm("")} disabled={busy}>
              Close
            </button>
          </div>
        </form>
      )}

      <p className="device-muted">
        Only the school administrator can remove a registered device. You can ask
        above; you will be notified of the decision.
      </p>

      {requests.length > 0 && (
        <div className="device-requests">
          <h3>My removal requests</h3>
          <ul>
            {requests.map((r) => (
              <li key={r.id}>
                <div>
                  <strong>{r.requestType === "EMERGENCY" ? "EMERGENCY" : "Normal"}</strong>{" "}
                  · {REASON_LABELS[r.reason] || r.reason} ·{" "}
                  <span className={`device-status ${String(r.status).toLowerCase()}`}>
                    {r.status}
                  </span>
                </div>
                <small>
                  {formatDateTime(r.requestedAt)}
                  {r.adminComment ? ` · Administrator: ${r.adminComment}` : ""}
                </small>
                {r.status === "PENDING" && r.requestType === "NORMAL" && (
                  <button type="button" onClick={() => handleCancel(r.id)} disabled={busy}>
                    Cancel request
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {history.length > 0 && (
        <details className="device-history">
          <summary>Previous devices ({history.length})</summary>
          <ul>
            {history.map((item) => (
              <li key={item.biometricId}>
                {item.deviceName || item.fingerName || "Device"} ·{" "}
                <span className="device-status revoked">REVOKED</span> ·{" "}
                {formatDate(item.revokedAt)}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

export default BiometricSettings;

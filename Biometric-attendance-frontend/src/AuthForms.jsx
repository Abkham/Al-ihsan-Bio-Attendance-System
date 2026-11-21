import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { loginEmployee, registerEmployeeAccount } from "./api/employeeApi";
import { loginAdmin } from "./api/adminApi";
import {
  requestPasswordReset,
  checkResetToken,
  confirmPasswordReset,
} from "./api/authApi";
import { getErrorMessage } from "./api/api";
import { saveSession } from "./api/session";
import { checkPassword, PASSWORD_HINT } from "./api/passwordPolicy";

/*
  Login screen used by BOTH entrances: the employee page (/) and the administrator page (/admin).
  Each page shows only its own form; there is no role selection screen. The card has the
  "Sign In" / "Reset Password" choice.
*/

function Header() {
  return (
    <div className="school-header">
      <img
        src="/LOGO.JPG"
        alt="Alihsan Girls Secondary School Logo"
        className="school-logo"
      />
      <div className="school-name">
        <h1>AL-IHSAN GIRLS</h1>
        <h2>SECONDARY SCHOOL</h2>
      </div>
    </div>
  );
}

function PasswordInput({ id, value, onChange, placeholder, autoComplete }) {
  const [show, setShow] = useState(false);

  return (
    <div className="password-field">
      <input
        id={id}
        type={show ? "text" : "password"}
        placeholder={placeholder}
        value={value}
        autoComplete={autoComplete}
        onChange={(e) => onChange(e.target.value)}
      />
      <button
        type="button"
        className="password-toggle"
        onClick={() => setShow(!show)}
        aria-label={show ? "Hide password" : "Show password"}
      >
        {show ? "🔒" : "👁️"}
      </button>
    </div>
  );
}

export function LoginTabs({ role }) {
  const navigate = useNavigate();
  const isAdmin = role === "ADMIN";

  const [tab, setTab] = useState("signin");

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [signInError, setSignInError] = useState("");
  const [busy, setBusy] = useState(false);

  const [resetUser, setResetUser] = useState("");
  const [resetEmail, setResetEmail] = useState("");
  const [resetMessage, setResetMessage] = useState("");
  const [resetError, setResetError] = useState("");

  // ---- employee self-registration ----
  const [regId, setRegId] = useState("");
  const [regCode, setRegCode] = useState("");
  const [regUser, setRegUser] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [regConfirm, setRegConfirm] = useState("");
  const [regError, setRegError] = useState("");
  const [regDone, setRegDone] = useState("");

  const handleRegister = async (e) => {
    e.preventDefault();
    setRegError("");
    setRegDone("");

    if (!regId.trim() || !regCode.trim() || !regUser.trim() || !regPassword || !regConfirm) {
      setRegError("Please fill in every field.");
      return;
    }

    const problem = checkPassword(regPassword, regUser);

    if (problem) {
      setRegError(problem);
      return;
    }

    if (regPassword !== regConfirm) {
      setRegError("The two passwords do not match.");
      return;
    }

    setBusy(true);

    try {
      const result = await registerEmployeeAccount({
        employeeId: regId.trim(),
        activationCode: regCode.trim(),
        username: regUser.trim(),
        password: regPassword,
        confirmPassword: regConfirm,
      });

      setRegDone(result?.message || "Your account was created. You can now log in.");
      setUsername(regUser.trim());
      setRegId("");
      setRegCode("");
      setRegUser("");
      setRegPassword("");
      setRegConfirm("");
      setTab("signin");
    } catch (error) {
      setRegError(getErrorMessage(error, "Your account could not be created. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  const handleSignIn = async (e) => {
    e.preventDefault();
    setSignInError("");

    if (!username.trim() || !password) {
      setSignInError(
        isAdmin
          ? "Please enter administrator credentials."
          : "Please enter username and password."
      );
      return;
    }

    setBusy(true);

    try {
      if (isAdmin) {
        const admin = await loginAdmin(username.trim(), password);
        saveSession("admin", admin);
        navigate("/admin/dashboard");
      } else {
        const employee = await loginEmployee(username.trim(), password);
        saveSession("employee", employee);
        navigate("/employee-dashboard");
      }
    } catch (error) {
      setSignInError(
        getErrorMessage(
          error,
          isAdmin
            ? "Login failed. Username or password is not recognized."
            : "Login failed. Employee ID/Username or password is not recognized."
        )
      );
    } finally {
      setBusy(false);
    }
  };

  const handleReset = async (e) => {
    e.preventDefault();
    setResetError("");
    setResetMessage("");

    if (!resetUser.trim() || !resetEmail.trim()) {
      setResetError("Please enter your username and your registered e-mail address.");
      return;
    }

    setBusy(true);

    try {
      const result = await requestPasswordReset(
        role,
        resetUser.trim(),
        resetEmail.trim()
      );
      setResetMessage(result?.message || "Request received.");
    } catch (error) {
      setResetError(
        getErrorMessage(error, "The request could not be sent. Please try again.")
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="auth-card">
        <Header />

        <h1>{isAdmin ? "Administrator Login" : "Employee Login"}</h1>

        <p className="auth-subtitle">
          {isAdmin
            ? "Login to manage the attendance system"
            : "Login to access your attendance system"}
        </p>

        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === "signin"}
            className={`auth-tab ${tab === "signin" ? "active" : ""}`}
            onClick={() => setTab("signin")}
          >
            Sign In
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "reset"}
            className={`auth-tab ${tab === "reset" ? "active" : ""}`}
            onClick={() => setTab("reset")}
          >
            Reset Password
          </button>
          {!isAdmin && (
            <button
              type="button"
              role="tab"
              aria-selected={tab === "register"}
              className={`auth-tab ${tab === "register" ? "active" : ""}`}
              onClick={() => setTab("register")}
            >
              Register
            </button>
          )}
        </div>

        {tab === "signin" && regDone && (
          <p className="form-message success" role="status">
            {regDone}
          </p>
        )}

        {tab === "register" && !isAdmin ? (
          <form onSubmit={handleRegister}>
            <p className="form-message info">
              First time here? Create your own account. You need your Employee ID and
              the one-time activation code the school administrator gave you.
            </p>

            <label htmlFor="reg-id">Employee ID</label>
            <input
              id="reg-id"
              type="text"
              autoComplete="off"
              placeholder="Example: EMP004"
              value={regId}
              onChange={(e) => setRegId(e.target.value)}
            />

            <label htmlFor="reg-code">Activation code</label>
            <input
              id="reg-code"
              type="text"
              autoComplete="off"
              placeholder="XXXXX-XXXXX"
              value={regCode}
              onChange={(e) => setRegCode(e.target.value)}
            />

            <label htmlFor="reg-user">Choose a username</label>
            <input
              id="reg-user"
              type="text"
              autoComplete="username"
              placeholder="3-50 letters, numbers, . _ -"
              value={regUser}
              onChange={(e) => setRegUser(e.target.value)}
            />

            <label htmlFor="reg-password">Password</label>
            <PasswordInput
              id="reg-password"
              value={regPassword}
              onChange={setRegPassword}
              placeholder="Create a password"
              autoComplete="new-password"
            />
            <small className="form-hint">{PASSWORD_HINT}</small>

            <label htmlFor="reg-confirm">Confirm password</label>
            <PasswordInput
              id="reg-confirm"
              value={regConfirm}
              onChange={setRegConfirm}
              placeholder="Type the password again"
              autoComplete="new-password"
            />

            {regError && (
              <p className="form-message error" role="alert">
                {regError}
              </p>
            )}

            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? "Please wait..." : "Create my account"}
            </button>

            <button type="button" className="link-button" onClick={() => setTab("signin")}>
              Already have an account? Sign in
            </button>
          </form>
        ) : tab === "signin" ? (
          <form onSubmit={handleSignIn}>
            <label htmlFor="login-user">
              {isAdmin ? "Administrator Username" : "Employee ID / Username"}
            </label>
            <input
              id="login-user"
              type="text"
              autoComplete="username"
              placeholder={isAdmin ? "Enter username" : "Enter employee ID"}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />

            <label htmlFor="login-password">Password</label>
            <PasswordInput
              id="login-password"
              value={password}
              onChange={setPassword}
              placeholder="Enter password"
              autoComplete="current-password"
            />

            {signInError && (
              <p className="form-message error" role="alert">
                {signInError}
              </p>
            )}

            <button type="submit" className="primary-button" disabled={busy}>
              {busy
                ? "Please wait..."
                : isAdmin
                  ? "Login as Administrator"
                  : "Login"}
            </button>

            <button
              type="button"
              className="link-button"
              onClick={() => setTab("reset")}
            >
              Forgot your password?
            </button>

            {!isAdmin && (
              <button
                type="button"
                className="link-button"
                onClick={() => setTab("register")}
              >
                New employee? Register
              </button>
            )}
          </form>
        ) : (
          <form onSubmit={handleReset}>
            <p className="form-message info">
              Enter your {isAdmin ? "username" : "Employee ID or username"} and the
              e-mail address registered on your account. If they match, we send a
              one-time link to that e-mail. The link expires and works only once.
            </p>

            <label htmlFor="reset-user">
              {isAdmin ? "Administrator Username" : "Employee ID / Username"}
            </label>
            <input
              id="reset-user"
              type="text"
              autoComplete="username"
              value={resetUser}
              onChange={(e) => setResetUser(e.target.value)}
            />

            <label htmlFor="reset-email">Registered e-mail address</label>
            <input
              id="reset-email"
              type="email"
              autoComplete="email"
              value={resetEmail}
              onChange={(e) => setResetEmail(e.target.value)}
            />

            {resetError && (
              <p className="form-message error" role="alert">
                {resetError}
              </p>
            )}

            {resetMessage && (
              <p className="form-message success" role="status">
                {resetMessage}
              </p>
            )}

            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? "Please wait..." : "Send reset link"}
            </button>

            <p className="form-message muted">
              No e-mail on your account? Ask the school administrator to set a new
              password for you.
            </p>
          </form>
        )}

      </div>
    </div>
  );
}

/*
  Page opened from the e-mail link:  /reset-password?token=...
  The token is removed from the address bar immediately so it is not kept in the
  browser history or sent to other sites.
*/
export function ResetPasswordPage({ loginPath = "/", resetPath = "/reset-password" }) {
  const navigate = useNavigate();

  const [token] = useState(() => {
    try {
      return new URLSearchParams(window.location.search).get("token") || "";
    } catch {
      return "";
    }
  });

  const [state, setState] = useState(token ? "checking" : "invalid");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState("");

  useEffect(() => {
    if (token) {
      window.history.replaceState(null, "", resetPath);

      checkResetToken(token)
        .then((valid) => setState(valid ? "ready" : "invalid"))
        .catch(() => setState("invalid"));
    }
  }, [token, resetPath]);

  const submit = async (e) => {
    e.preventDefault();
    setError("");

    const problem = checkPassword(newPassword);

    if (problem) {
      setError(problem);
      return;
    }

    if (newPassword !== confirm) {
      setError("The two passwords do not match.");
      return;
    }

    setBusy(true);

    try {
      const result = await confirmPasswordReset(token, newPassword, confirm);
      setDone(result?.message || "Your password has been changed.");
    } catch (err) {
      setError(getErrorMessage(err, "The password could not be changed."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="auth-card">
        <Header />

        <h1>Reset Password</h1>

        {state === "checking" && <p className="auth-subtitle">Checking your link...</p>}

        {state === "invalid" && (
          <>
            <p className="form-message error" role="alert">
              This reset link is invalid or has expired. Please request a new one
              from the login page.
            </p>
            <button className="primary-button" onClick={() => navigate(loginPath)}>
              Back to Login
            </button>
          </>
        )}

        {state === "ready" && !done && (
          <form onSubmit={submit}>
            <p className="auth-subtitle">Choose a new password for your account.</p>

            <label htmlFor="new-password">New password</label>
            <PasswordInput
              id="new-password"
              value={newPassword}
              onChange={setNewPassword}
              placeholder="New password"
              autoComplete="new-password"
            />

            <label htmlFor="confirm-password">Confirm new password</label>
            <PasswordInput
              id="confirm-password"
              value={confirm}
              onChange={setConfirm}
              placeholder="Repeat the new password"
              autoComplete="new-password"
            />

            <p className="form-message muted">{PASSWORD_HINT}</p>

            {error && (
              <p className="form-message error" role="alert">
                {error}
              </p>
            )}

            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? "Please wait..." : "Change password"}
            </button>
          </form>
        )}

        {done && (
          <>
            <p className="form-message success" role="status">
              {done}
            </p>
            <button className="primary-button" onClick={() => navigate(loginPath)}>
              Go to Login
            </button>
          </>
        )}
      </div>
    </div>
  );
}

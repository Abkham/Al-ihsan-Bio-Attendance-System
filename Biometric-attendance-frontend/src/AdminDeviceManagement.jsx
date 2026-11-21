import React, { useCallback, useEffect, useMemo, useState } from "react";
import { getAllBiometrics, adminResetDevice } from "./api/biometricApi";
import {
  getAllRemovalRequests,
  approveRemovalRequest,
  rejectRemovalRequest,
  emergencyRevoke,
  issueRecoveryCode,
  getAuditLogs,
} from "./api/deviceRequestApi";
import { getErrorMessage } from "./api/api";

/*
  Administrator -> "Device Management".
    Requests : pending removal requests (EMERGENCY reports are shown first and in red)
    Devices  : every registered device (ACTIVE) and the revoked history, searchable
    History  : audit log of device and account actions
  The administrator is the only person who can remove a device.
*/

const REASONS = {
  NEW_PHONE: "Bought a new phone",
  DAMAGED_DEVICE: "Device damaged",
  BIOMETRIC_PROBLEM: "Biometric problems",
  OTHER: "Other reason",
  STOLEN: "Phone stolen",
  LOST: "Phone lost",
};

const fmt = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString();
};

const badge = (status) => (
  <span className={`dm-badge dm-${String(status || "").toLowerCase()}`}>
    {String(status || "").toUpperCase()}
  </span>
);

export default function AdminDeviceManagement({ onChanged }) {
  const [tab, setTab] = useState("requests");
  const [requests, setRequests] = useState([]);
  const [devices, setDevices] = useState([]);
  const [logs, setLogs] = useState([]);
  const [search, setSearch] = useState("");
  const [showClosed, setShowClosed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [codeBox, setCodeBox] = useState(null); // { who, code }
  const [details, setDetails] = useState(null);

  const load = useCallback(async () => {
    try {
      const [r, d, l] = await Promise.all([
        getAllRemovalRequests(),
        getAllBiometrics(),
        getAuditLogs().catch(() => []),
      ]);
      setRequests(Array.isArray(r) ? r : []);
      setDevices(Array.isArray(d) ? d : []);
      setLogs(Array.isArray(l) ? l : []);
      setError("");
    } catch (e) {
      setError(getErrorMessage(e, "Device information could not be loaded."));
    }
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, 30000); // emergency reports appear without a manual refresh
    return () => clearInterval(timer);
  }, [load]);

  const run = async (job, doneText) => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await job();
      if (doneText) setNotice(doneText);
      await load();
      if (onChanged) onChanged();
      return result;
    } catch (e) {
      setError(getErrorMessage(e, "The action could not be completed."));
      return null;
    } finally {
      setBusy(false);
    }
  };

  const pending = useMemo(
    () => requests.filter((r) => r.status === "PENDING"),
    [requests]
  );
  const emergencies = pending.filter((r) => r.requestType === "EMERGENCY");

  const visibleRequests = useMemo(() => {
    const list = showClosed ? requests : pending;
    return [...list].sort((a, b) => {
      if (a.requestType !== b.requestType) return a.requestType === "EMERGENCY" ? -1 : 1;
      return new Date(b.requestedAt) - new Date(a.requestedAt);
    });
  }, [requests, pending, showClosed]);

  const matches = (text) => String(text || "").toLowerCase().includes(search.trim().toLowerCase());

  const visibleDevices = devices.filter(
    (d) => !search.trim() || matches(d.employeeId) || matches(d.employeeName) || matches(d.deviceName)
  );

  const visibleLogs = logs.filter(
    (l) => !search.trim() || matches(l.targetEmployeeId) || matches(l.action) || matches(l.description)
  );

  const approve = (r) => {
    const comment = window.prompt("Optional note for the employee:", "");
    if (comment === null) return;
    run(() => approveRemovalRequest(r.id, comment), `Approved. ${r.employeeName}'s old device was revoked and they were notified.`);
  };

  const reject = (r) => {
    const comment = window.prompt("Why is the request rejected? (the employee will see this)", "");
    if (comment === null) return;
    if (comment.trim().length < 3) {
      setError("Please write the reason for rejecting the request.");
      return;
    }
    run(() => rejectRemovalRequest(r.id, comment), "The request was rejected. The device stays registered.");
  };

  const revokeNow = async (r) => {
    if (!window.confirm(`Revoke ${r.employeeName}'s device IMMEDIATELY? The phone will stop working for attendance at once.`)) return;
    const result = await run(() => emergencyRevoke(r.id, "Emergency revocation by administrator"), "The device was revoked.");
    if (result?.verificationCode) {
      setCodeBox({ who: `${r.employeeName} (${r.employeeId})`, code: result.verificationCode });
    }
  };

  const newCode = async (employeeId, name) => {
    const result = await run(() => issueRecoveryCode(employeeId), "");
    if (result?.verificationCode) setCodeBox({ who: `${name} (${employeeId})`, code: result.verificationCode });
  };

  const directReset = (d) => {
    if (!window.confirm(`Remove ${d.employeeName || d.employeeId}'s registered device? They can then register a new one.`)) return;
    run(() => adminResetDevice(d.employeeId, "Reset by administrator"), "The device was removed.");
  };

  return (
    <div className="dm-wrap">
      <h2>Device Management</h2>

      {emergencies.length > 0 && (
        <div className="dm-alert" role="alert">
          🚨 {emergencies.length} EMERGENCY report{emergencies.length > 1 ? "s" : ""} waiting:
          {" "}
          {emergencies.map((e) => e.employeeName).join(", ")}
        </div>
      )}

      {error && <div className="dm-error" role="alert">{error}</div>}
      {notice && <div className="dm-notice" role="status">{notice}</div>}

      {codeBox && (
        <div className="dm-code" role="alert">
          <div>
            Verification code for <strong>{codeBox.who}</strong> (shown only now):
          </div>
          <div className="dm-code-value">{codeBox.code}</div>
          <small>
            First confirm the employee's identity in person or by phone, then give them this
            code. It works until a new device is registered and expires in 3 days.
          </small>
          <div>
            <button type="button" onClick={() => setCodeBox(null)}>I have noted it</button>
          </div>
        </div>
      )}

      <div className="dm-tabs">
        <button type="button" className={tab === "requests" ? "active" : ""} onClick={() => setTab("requests")}>
          Removal requests ({pending.length})
        </button>
        <button type="button" className={tab === "devices" ? "active" : ""} onClick={() => setTab("devices")}>
          Devices
        </button>
        <button type="button" className={tab === "history" ? "active" : ""} onClick={() => setTab("history")}>
          History / audit
        </button>
      </div>

      {tab !== "requests" && (
        <input
          className="dm-search"
          type="text"
          placeholder="Search by employee name or Employee ID..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      )}

      {tab === "requests" && (
        <>
          <label className="dm-check">
            <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />
            {" "}Show handled requests too
          </label>

          {visibleRequests.length === 0 ? (
            <p className="dm-muted">No {showClosed ? "" : "pending "}removal requests.</p>
          ) : (
            <div className="dm-scroll">
              <table className="dm-table">
                <thead>
                  <tr>
                    <th>Type</th><th>Employee</th><th>Device</th><th>Reason</th>
                    <th>Explanation</th><th>Requested</th><th>Status</th><th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRequests.map((r) => (
                    <tr key={r.id} className={r.requestType === "EMERGENCY" && r.status === "PENDING" ? "dm-row-emergency" : ""}>
                      <td>{r.requestType === "EMERGENCY" ? <strong className="dm-emergency">EMERGENCY</strong> : "Normal"}</td>
                      <td>{r.employeeName}<br /><small>{r.employeeId}</small></td>
                      <td>{r.deviceName || "-"}</td>
                      <td>{REASONS[r.reason] || r.reason}</td>
                      <td>{r.description || "-"}{r.adminComment ? <><br /><small>Admin: {r.adminComment}</small></> : null}</td>
                      <td>{fmt(r.requestedAt)}</td>
                      <td>{badge(r.status)}</td>
                      <td className="dm-actions">
                        {r.status === "PENDING" && r.requestType === "NORMAL" && (
                          <>
                            <button type="button" disabled={busy} onClick={() => approve(r)}>Approve</button>
                            <button type="button" disabled={busy} className="danger" onClick={() => reject(r)}>Reject</button>
                          </>
                        )}
                        {r.status === "PENDING" && r.requestType === "EMERGENCY" && (
                          <>
                            <button type="button" disabled={busy} className="danger" onClick={() => revokeNow(r)}>Revoke immediately</button>
                            <button type="button" disabled={busy} onClick={() => reject(r)}>Dismiss</button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === "devices" && (
        <div className="dm-scroll">
          <table className="dm-table">
            <thead>
              <tr>
                <th>Employee</th><th>Device name</th><th>Status</th><th>Registered</th><th>Last used</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {visibleDevices.length === 0 && (
                <tr><td colSpan={6} className="dm-muted">No devices found.</td></tr>
              )}
              {visibleDevices.map((d) => (
                <tr key={d.biometricId}>
                  <td>{d.employeeName || "-"}<br /><small>{d.employeeId}</small></td>
                  <td>{d.deviceName || d.fingerName || "-"}</td>
                  <td>{badge(d.status)}</td>
                  <td>{fmt(d.registeredAt)}</td>
                  <td>{fmt(d.lastUsedAt)}</td>
                  <td className="dm-actions">
                    <button type="button" onClick={() => setDetails(d)}>Details</button>
                    {d.status === "Active" && (
                      <button type="button" className="danger" disabled={busy} onClick={() => directReset(d)}>Remove device</button>
                    )}
                    {d.status !== "Active" && !devices.some((x) => x.employeeId === d.employeeId && x.status === "Active") && (
                      <button type="button" disabled={busy} onClick={() => newCode(d.employeeId, d.employeeName)}
                        title="Only needed after an emergency revocation">
                        New verification code
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === "history" && (
        <div className="dm-scroll">
          <table className="dm-table">
            <thead>
              <tr><th>When</th><th>Action</th><th>By</th><th>Employee</th><th>Details</th></tr>
            </thead>
            <tbody>
              {visibleLogs.length === 0 && (
                <tr><td colSpan={5} className="dm-muted">No history yet.</td></tr>
              )}
              {visibleLogs.map((l) => (
                <tr key={l.id}>
                  <td>{fmt(l.createdAt)}</td>
                  <td>{String(l.action || "").replaceAll("_", " ")}</td>
                  <td>{l.actorRole}{l.actorId ? ` (${l.actorId})` : ""}</td>
                  <td>{l.targetEmployeeId || "-"}</td>
                  <td>{l.description || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {details && (
        <div className="dm-modal" role="dialog" aria-modal="true" onClick={() => setDetails(null)}>
          <div className="dm-modal-card" onClick={(e) => e.stopPropagation()}>
            <h3>{details.deviceName || details.fingerName || "Device"}</h3>
            <dl>
              <dt>Employee</dt><dd>{details.employeeName} ({details.employeeId})</dd>
              <dt>Status</dt><dd>{badge(details.status)}</dd>
              <dt>Authentication</dt><dd>{details.authenticationMethod}</dd>
              <dt>Browser</dt><dd>{details.browser || "-"}</dd>
              <dt>Operating system</dt><dd>{details.operatingSystem || "-"}</dd>
              <dt>Registered</dt><dd>{fmt(details.registeredAt)}</dd>
              <dt>Last used</dt><dd>{fmt(details.lastUsedAt)}</dd>
              {details.status !== "Active" && (
                <>
                  <dt>Revoked</dt><dd>{fmt(details.revokedAt)} by {details.revokedBy || "-"}</dd>
                  <dt>Reason</dt><dd>{details.revokeReason || "-"}</dd>
                </>
              )}
            </dl>
            <button type="button" onClick={() => setDetails(null)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

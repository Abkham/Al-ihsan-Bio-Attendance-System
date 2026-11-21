import api from "./api";

const id = (value) => encodeURIComponent(value);

// ---------------- employee ----------------

// type: "NORMAL" (needs a fingerprint check first) | "EMERGENCY" (stolen / lost phone)
export const submitRemovalRequest = async (employeeId, payload) => {
  const response = await api.post(`/device-requests/employee/${id(employeeId)}`, payload);
  return response.data;
};

export const getMyRemovalRequests = async (employeeId) => {
  const response = await api.get(`/device-requests/employee/${id(employeeId)}`);
  return response.data;
};

export const cancelRemovalRequest = async (employeeId, requestId) => {
  const response = await api.post(
    `/device-requests/employee/${id(employeeId)}/${id(requestId)}/cancel`
  );
  return response.data;
};

export const getMyNotifications = async (employeeId) => {
  const response = await api.get(`/notifications/employee/${id(employeeId)}`);
  return response.data;
};

export const markNotificationsRead = async (employeeId) => {
  const response = await api.post(`/notifications/employee/${id(employeeId)}/read-all`);
  return response.data;
};

export const getRecoveryStatus = async (employeeId) => {
  const response = await api.get(`/devices/employee/${id(employeeId)}/recovery-status`);
  return response.data;
};

export const verifyRecoveryCode = async (employeeId, code) => {
  const response = await api.post(`/devices/employee/${id(employeeId)}/verify-recovery`, { code });
  return response.data;
};

// ---------------- administrator ----------------

export const getAllRemovalRequests = async () => {
  const response = await api.get("/device-requests");
  return response.data;
};

export const approveRemovalRequest = async (requestId, comment = "") => {
  const response = await api.post(`/device-requests/${id(requestId)}/approve`, { comment });
  return response.data;
};

export const rejectRemovalRequest = async (requestId, comment) => {
  const response = await api.post(`/device-requests/${id(requestId)}/reject`, { comment });
  return response.data;
};

// Returns { request, verificationCode, message } - the code is shown only once.
export const emergencyRevoke = async (requestId, comment = "") => {
  const response = await api.post(`/device-requests/${id(requestId)}/emergency-revoke`, { comment });
  return response.data;
};

export const issueRecoveryCode = async (employeeId) => {
  const response = await api.post(`/device-requests/recovery-code/${id(employeeId)}`);
  return response.data;
};

export const getAuditLogs = async (employeeId = "") => {
  const response = await api.get("/audit-logs", {
    params: employeeId ? { employeeId } : {},
  });
  return response.data;
};

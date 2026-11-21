import api from "./api";

export const getAllBiometrics = async () => {
  const response = await api.get("/biometrics");
  return response.data;
};

export const getEmployeeBiometrics = async (employeeId) => {
  const response = await api.get(
    `/biometrics/employee/${encodeURIComponent(employeeId)}`
  );
  return response.data;
};

export const registerBiometric = async (employeeId, biometric) => {
  const response = await api.post(
    `/biometrics/employee/${encodeURIComponent(employeeId)}`,
    biometric
  );
  return response.data;
};

// Kept as a harmless no-op: the server no longer has a "mark used" endpoint.
export const markBiometricUsed = async () => null;

// Administrator unlocks an employee's account for a NEW device (lost / replaced phone).
export const adminResetDevice = async (employeeId, reason = "") => {
  const response = await api.post(
    `/devices/employee/${encodeURIComponent(employeeId)}/revoke`,
    { reason }
  );
  return response.data;
};

// Display name only (the security of the device never depends on it).
export const renameMyDevice = async (employeeId, deviceNickname) => {
  const response = await api.patch(
    `/devices/employee/${encodeURIComponent(employeeId)}/nickname`,
    { deviceNickname }
  );
  return response.data;
};

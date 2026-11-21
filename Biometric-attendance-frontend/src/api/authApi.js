import api from "./api";

/*
  Password reset + session helpers shared by employees and administrators.
  role = "EMPLOYEE" (main login page) | "ADMIN" (login page at /admin).
*/

// Always answers with the same generic message (the server never says whether an account exists).
export const requestPasswordReset = async (role, username, email) => {
  const response = await api.post("/auth/forgot-password", {
    role,
    username,
    email,
  });
  return response.data;
};

export const checkResetToken = async (token) => {
  const response = await api.post("/auth/reset-password/check", { token });
  return Boolean(response.data?.valid);
};

export const confirmPasswordReset = async (
  token,
  newPassword,
  confirmPassword
) => {
  const response = await api.post("/auth/reset-password", {
    token,
    newPassword,
    confirmPassword,
  });
  return response.data;
};

// Which role is this browser really signed in as? { authenticated: boolean, role: "ADMIN" | "EMPLOYEE" | null }
export const getServerSession = async () => {
  const response = await api.get("/auth/session");
  return response.data;
};

export const logoutServer = async () => {
  try {
    await api.post("/auth/logout");
  } catch {
    /* already signed out */
  }
};

// The school's own date (Africa/Dar_es_Salaam by default), not the phone's clock.
export const getSchoolTime = async () => {
  const response = await api.get("/auth/school-time");
  return response.data;
};

export const getSupportContact = async () => {
  const response = await api.get("/support/contact");
  return response.data;
};

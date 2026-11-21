// Same rule as the server (PasswordPolicy.java). The server is the real check.
export const PASSWORD_HINT =
  "At least 8 characters, with at least one letter and one number.";

export const checkPassword = (password, username = "") => {
  if (!password) return "Password is required.";

  if (password.length < 8) return PASSWORD_HINT;

  if (!/[A-Za-zÀ-￿]/.test(password) || !/[0-9]/.test(password)) {
    return PASSWORD_HINT;
  }

  if (
    username &&
    username.trim().length >= 3 &&
    password.toLowerCase().includes(username.trim().toLowerCase())
  ) {
    return "Password must not contain your username.";
  }

  return "";
};

import axios from "axios";

/*
  ONE place that decides where the Spring Boot backend is.

  - Same PC:            http://localhost:8080/api
  - Phone on same WiFi: the page is opened as http://192.168.x.x:5173, so the
                        backend is automatically http://192.168.x.x:8080/api
  - Custom URL:         create a file ".env" with  VITE_API_URL=http://server:8080/api
*/
const API_BASE_URL =
  import.meta.env.VITE_API_URL ||
  (window.location.protocol === "https:"
    ? `${window.location.origin}/api`   // HTTPS tunnel: Vite proxies /api to Spring Boot
    : `http://${window.location.hostname}:8080/api`);
  export const BACKEND_BASE_URL = API_BASE_URL.replace(/\/api\/?$/, "");

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
    // Required by the server for every state-changing request (cross-site request protection).
    "X-Requested-With": "XMLHttpRequest",
  },
});

/*
  If the SERVER says the session is gone (password changed/reset elsewhere,
  server restarted, account removed) clear the browser copy and return to the
  start page, instead of showing broken screens.
*/
const PUBLIC_PATHS = ["/login", "/auth/", "/support/"];

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const code = error?.response?.data?.code;
    const url = error?.config?.url || "";

    if (
      status === 401 &&
      (code === "SESSION_REVOKED" || code === "NOT_AUTHENTICATED") &&
      !PUBLIC_PATHS.some((part) => url.includes(part))
    ) {
      try {
        sessionStorage.removeItem("session_admin");
        sessionStorage.removeItem("session_employee");
      } catch {
        /* storage not available - nothing to clear */
      }

      // Administrators go back to their own page, employees to the main page.
      const path = window.location.pathname;
      const adminSide = path === "/admin" || path.startsWith("/admin/");
      const target = adminSide ? "/admin" : "/";

      if (path !== target && !path.includes("login")) {
        window.location.assign(target);
      }
    }

    return Promise.reject(error);
  }
);

/*
  Turn any axios error into ONE readable message.
  Spring Boot answers errors as { "message": "..." }.
*/
export const getErrorMessage = (error, fallback) => {
  if (error?.response?.data?.message) {
    return error.response.data.message;
  }

  if (typeof error?.response?.data === "string" && error.response.data) {
    return error.response.data;
  }

  if (error?.code === "ERR_NETWORK" || !error?.response) {
    return "Cannot reach the server. Make sure the Spring Boot backend and MySQL are running.";
  }

  return (
    fallback ||
    error?.message ||
    "Something went wrong. Please try again."
  );
};

export default api;

import React, { Suspense } from "react";
import {
  Routes,
  Route,
  useNavigate,
  Navigate,
} from "react-router-dom";

import "./App.css";
import Dashboard from "./Dashboard";

import { LoginTabs, ResetPasswordPage } from "./AuthForms";
import { logoutServer } from "./api/authApi";
import { getSession, clearSession } from "./api/session";
import useVerifiedRole from "./useVerifiedRole";

/*
  TWO SEPARATE ENTRANCES, ONE SYSTEM (no role selection screen)

    Employees      open the main address      /                 -> employee login only
    Administrators open their own address     /admin            -> administrator login only

  - The employee pages never show an administrator link or form, and the administrator
    pages never show the employee form.
  - The administrator screens are loaded on demand from "AdminArea": an employee's browser
    does not even download the administrator code unless somebody opens /admin.
  - Hiding a page is NOT what protects the data. The server decides: every administrator
    request needs an administrator session (an employee session gets "403 forbidden"), and an
    employee can only read or change their own records.
*/

/* =========================
   EMPLOYEE LOGIN (main address)
========================= */

function EmployeeEntry() {

  // Already signed in on this tab: go straight to your own area.
  if (getSession("employee")) {
    return <Navigate to="/employee-dashboard" replace />;
  }

  if (getSession("admin")) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  return <LoginTabs role="EMPLOYEE" />;
}


/* =========================
   PROTECTED EMPLOYEE DASHBOARD
========================= */

function EmployeeDashboardPage() {

  const navigate = useNavigate();

  const employee = getSession("employee");

  // The SERVER must confirm this really is an employee session (the browser copy can be edited).
  const verified = useVerifiedRole("EMPLOYEE");

  /*
    Protect the employee dashboard: no valid session = back to login.
  */
  if (!employee || verified === "denied") {

    if (employee) clearSession("employee");

    return (
      <Navigate
        to="/"
        replace
      />
    );
  }

  if (verified === "checking") {
    return null;
  }

  return (

    <Dashboard
      employee={employee}
      onLogout={() => {

        clearSession("employee");
        logoutServer();

        navigate("/", { replace: true });

      }}
    />

  );
}


/* =========================
   ADMINISTRATOR AREA (loaded only when /admin is opened)
========================= */

const AdminArea = React.lazy(() => import("./AdminArea"));


/* =========================
   APP ROUTES
========================= */

function App() {

  return (

    <Routes>

      {/* EMPLOYEE LOGIN */}

      <Route
        path="/"
        element={<EmployeeEntry />}
      />

      {/* EMPLOYEE DASHBOARD */}

      <Route
        path="/employee-dashboard"
        element={<EmployeeDashboardPage />}
      />

      {/* PASSWORD RESET LINK (employees) */}

      <Route
        path="/reset-password"
        element={<ResetPasswordPage />}
      />

      {/* ADMINISTRATOR AREA: its own address */}

      <Route
        path="/admin/*"
        element={
          <Suspense fallback={null}>
            <AdminArea />
          </Suspense>
        }
      />

      {/* OLD ADDRESS KEPT WORKING */}

      <Route
        path="/employee-login"
        element={<Navigate to="/" replace />}
      />

      {/* UNKNOWN ADDRESS */}

      <Route
        path="*"
        element={<Navigate to="/" replace />}
      />

    </Routes>

  );
}


export default App;

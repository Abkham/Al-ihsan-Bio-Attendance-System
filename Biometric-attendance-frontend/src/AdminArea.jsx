import React, { useEffect } from "react";
import { Routes, Route, Navigate } from "react-router-dom";

import AdminDashboard from "./AdminDashboard";
import { LoginTabs, ResetPasswordPage } from "./AuthForms";
import { getSession, clearSession } from "./api/session";
import useVerifiedRole from "./useVerifiedRole";

/*
  Everything the administrator sees lives under /admin:

    /admin                  administrator login (nothing about employees)
    /admin/dashboard        administrator dashboard (needs an administrator session)
    /admin/reset-password   page opened from the e-mail link sent to an administrator

  This file is loaded on demand (see App.jsx), so employees never download it
  unless they open /admin themselves. The real protection is on the server.
*/

function AdminEntry() {

  useEffect(() => {
    document.title = "BioAttend | Administrator";
  }, []);

  if (getSession("admin")) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  // An employee who is signed in stays in the employee area.
  if (getSession("employee")) {
    return <Navigate to="/employee-dashboard" replace />;
  }

  return <LoginTabs role="ADMIN" />;
}

function AdminDashboardPage() {

  const admin = getSession("admin");

  // The SERVER must confirm this really is an administrator session. An employee who edits the
  // browser storage, or who opens this address while signed in as an employee, is turned away.
  const verified = useVerifiedRole("ADMIN");

  if (!admin || verified === "denied") {

    if (admin) clearSession("admin");

    return <Navigate to="/admin" replace />;
  }

  if (verified === "checking") {
    return null;
  }

  return <AdminDashboard admin={admin} />;
}

function AdminArea() {

  return (
    <Routes>

      <Route index element={<AdminEntry />} />

      <Route path="dashboard" element={<AdminDashboardPage />} />

      <Route
        path="reset-password"
        element={
          <ResetPasswordPage
            loginPath="/admin"
            resetPath="/admin/reset-password"
          />
        }
      />

      {/* old address */}
      <Route path="login" element={<Navigate to="/admin" replace />} />

      <Route path="*" element={<Navigate to="/admin" replace />} />

    </Routes>
  );
}

export default AdminArea;

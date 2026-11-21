import { useEffect, useState } from "react";
import { getServerSession } from "./api/authApi";

/*
  Asks the SERVER which role this browser is really signed in as, before a dashboard is shown.

  The copy of the login kept in sessionStorage is only a convenience and anybody can edit it in
  the browser's developer tools. So a dashboard is shown only when the server confirms the role:

    "checking"  the server has not answered yet (show nothing)
    "ok"        the server session really belongs to this role
    "denied"    no session, another role (for example an employee opening the administrator
                page), or the server could not be reached
*/
export default function useVerifiedRole(role) {
  const [state, setState] = useState("checking");

  useEffect(() => {
    let cancelled = false;

    getServerSession()
      .then((session) => {
        if (cancelled) return;
        setState(session?.authenticated && session.role === role ? "ok" : "denied");
      })
      .catch(() => {
        if (!cancelled) setState("denied");
      });

    return () => {
      cancelled = true;
    };
  }, [role]);

  return state;
}

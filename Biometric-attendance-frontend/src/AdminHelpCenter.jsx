import React from "react";
import HelpCenterView from "./HelpCenterView";
import { ADMIN_HELP } from "./helpContent";

function AdminHelpCenter() {
  return (
    <HelpCenterView
      content={ADMIN_HELP}
      showSupport={false}
    />
  );
}

export default AdminHelpCenter;
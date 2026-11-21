import React from "react";
import HelpCenterView from "./HelpCenterView";
import { EMPLOYEE_HELP } from "./helpContent";

function HelpCenter() {
  return (
    <HelpCenterView
      content={EMPLOYEE_HELP}
      showSupport={true}
    />
  );
}

export default HelpCenter;
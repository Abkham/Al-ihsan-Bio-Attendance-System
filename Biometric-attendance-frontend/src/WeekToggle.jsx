import React from "react";

/*
  Current Week / Previous Week switch shared by the two weekly charts
  (administrator: Overall Employees Weekly Report, employee: Summary weekly report).
  It only changes which week the existing chart shows; nothing is reloaded.
*/

const OPTIONS = [
  { key: "current", label: "Current Week" },
  { key: "previous", label: "Previous Week" },
];

function WeekToggle({ value, onChange }) {
  return (
    <div
      role="group"
      aria-label="Choose week"
      style={{
        display: "inline-flex",
        border: "1px solid #1565c0",
        borderRadius: "8px",
        overflow: "hidden",
        marginBottom: "14px",
      }}
    >
      {OPTIONS.map((option, index) => {
        const active = value === option.key;

        return (
          <button
            key={option.key}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.key)}
            style={{
              border: "none",
              borderLeft: index === 0 ? "none" : "1px solid #1565c0",
              background: active ? "#1565c0" : "#fff",
              color: active ? "#fff" : "#1565c0",
              padding: "8px 16px",
              fontSize: "13px",
              fontWeight: "bold",
              cursor: "pointer",
            }}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

export function WeekEmptyState({ week }) {
  return (
    <div
      role="status"
      style={{
        background: "#f4f8f6",
        border: "1px dashed #b7c7bf",
        borderRadius: "8px",
        padding: "40px 12px",
        textAlign: "center",
        color: "#4a5a52",
        fontSize: "14px",
      }}
    >
      No attendance records available for the {week === "previous" ? "previous" : "current"} week.
    </div>
  );
}

export default WeekToggle;

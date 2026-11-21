/*
  Calendar helpers that work on "YYYY-MM-DD" TEXT, never on the phone's local time zone.
  The school's own "today" comes from the server (GET /api/auth/school-time), so the
  dashboard shows the same day for everybody no matter where their device is set.
*/

const toUtc = (key) => {
  const [y, m, d] = String(key).split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};

export const keyOf = (date) => date.toISOString().slice(0, 10);

export const isKey = (value) => typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);

export const addDays = (key, days) => {
  const date = toUtc(key);
  date.setUTCDate(date.getUTCDate() + days);
  return keyOf(date);
};

// Monday of the week (Mon-Sun weeks) that contains the day.
export const mondayOf = (key) => {
  const day = toUtc(key).getUTCDay(); // 0 = Sunday
  return addDays(key, day === 0 ? -6 : 1 - day);
};

export const monthPrefix = (key) => String(key).slice(0, 7);

// Fallback used only until the server answers: the device's own date.
export const deviceToday = () => {
  const now = new Date();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${mm}-${dd}`;
};

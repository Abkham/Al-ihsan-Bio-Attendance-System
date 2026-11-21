// ONE way to show a person's name everywhere: First Middle Last (middle name optional).
export const fullNameOf = (person) => {
  if (!person) return "";

  if (person.fullName && String(person.fullName).trim()) {
    return String(person.fullName).trim();
  }

  return [person.firstName, person.middleName, person.lastName]
    .map((part) => String(part || "").trim())
    .filter(Boolean)
    .join(" ");
};

export const initialsOf = (person) => {
  const parts = fullNameOf(person).split(/\s+/).filter(Boolean);

  if (parts.length === 0) return "?";

  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";

  return (first + last).toUpperCase();
};

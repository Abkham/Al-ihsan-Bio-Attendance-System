import React, { useEffect, useMemo, useState } from "react";
import "./HelpCenterView.css";
import { getSupportContact } from "./api/authApi";

function textOf(topic) {
  return [
    topic.title,
    ...(topic.steps || []),
    ...(topic.notes || []),
    ...(topic.table || []).flat(),
  ]
    .join(" ")
    .toLowerCase();
}

function SupportBox() {
  const [contact, setContact] = useState(undefined);

  useEffect(() => {
    let cancelled = false;

    getSupportContact()
      .then((data) => {
        if (!cancelled) setContact(data || null);
      })
      .catch(() => {
        if (!cancelled) setContact(null);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const phone = contact?.phone;
  const email = contact?.email;
  const telHref = phone
    ? `tel:${phone.replace(/[^+\d]/g, "")}`
    : null;

  return (
    <div className="hc-support" aria-live="polite">
      <h3>📞 Need more help?</h3>

      {contact === undefined && (
        <p>Loading contact details...</p>
      )}

      {contact !== undefined && (
        <>
          <p>
            Contact the school administrator
            {contact?.contactName
              ? ` (${contact.contactName})`
              : ""}
            {contact?.schoolName
              ? ` at ${contact.schoolName}`
              : ""}.
          </p>

          <ul>
            <li>
              <strong>Phone:</strong>{" "}
              {phone ? (
                <a href={telHref}>{phone}</a>
              ) : (
                <em>
                  not set yet - the administrator can add it
                  in Settings (Support Phone)
                </em>
              )}
            </li>

            <li>
              <strong>E-mail:</strong>{" "}
              {email ? (
                <a href={`mailto:${email}`}>{email}</a>
              ) : (
                <em>not set yet</em>
              )}
            </li>

            {contact?.workStart && contact?.workEnd && (
              <li>
                <strong>School hours:</strong>{" "}
                {contact.workStart} - {contact.workEnd}
              </li>
            )}
          </ul>
        </>
      )}
    </div>
  );
}

function HelpCenterView({
  content,
  showSupport = false,
}) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(content.topics[0]?.id);

  const topics = useMemo(() => {
    const q = query.trim().toLowerCase();

    return q
      ? content.topics.filter((topic) =>
          textOf(topic).includes(q)
        )
      : content.topics;
  }, [query, content]);

  return (
    <div className="hc-container">
      <h2>{content.title}</h2>

      <p className="hc-intro">
        {content.intro}
      </p>

      <div className="hc-search">
        <input
          type="search"
          placeholder="Search help (e.g. device, password, map)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search help topics"
        />
      </div>

      <div className="hc-layout">
        <div className="hc-topics">
          {topics.length === 0 && (
            <p className="hc-empty">
              No help topic matches &quot;{query}&quot;.
            </p>
          )}

          {topics.map((topic) => {
            const isOpen = query.trim()
              ? true
              : open === topic.id;

            return (
              <section
                className={`hc-card ${isOpen ? "open" : ""}`}
                key={topic.id}
              >
                <button
                  type="button"
                  className="hc-card-head"
                  aria-expanded={isOpen}
                  onClick={() =>
                    setOpen(
                      isOpen && !query.trim()
                        ? null
                        : topic.id
                    )
                  }
                >
                  <span className="hc-icon" aria-hidden="true">
                    {topic.icon}
                  </span>

                  <span className="hc-title">
                    {topic.title}
                  </span>

                  <span className="hc-chevron" aria-hidden="true">
                    {isOpen ? "−" : "+"}
                  </span>
                </button>

                {isOpen && (
                  <div className="hc-card-body">
                    {topic.steps && (
                      <ol>
                        {topic.steps.map((step) => (
                          <li key={step}>{step}</li>
                        ))}
                      </ol>
                    )}

                    {topic.table && (
                      <table className="hc-table">
                        <thead>
                          <tr>
                            <th>Message / problem</th>
                            <th>What to do</th>
                          </tr>
                        </thead>

                        <tbody>
                          {topic.table.map(([problem, fix]) => (
                            <tr key={problem}>
                              <td>{problem}</td>
                              <td>{fix}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}

                    {topic.notes && (
                      <ul className="hc-notes">
                        {topic.notes.map((note) => (
                          <li key={note}>{note}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </section>
            );
          })}
        </div>

        {showSupport && (
          <div className="hc-support-section">
            <SupportBox />
          </div>
        )}
      </div>
    </div>
  );
}

export default HelpCenterView;
import React, { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

/*
  REAL map (Leaflet + OpenStreetMap tiles).

  - Employee markers are drawn at the latitude/longitude the server recorded when the
    employee signed in / out. Nothing is guessed or invented: a record without a valid
    point is simply not drawn (and counted in the note under the map).
  - The school is drawn differently (a "school" marker + a circle showing the allowed
    radius from the settings), so it is never confused with an employee.
  - No API key is needed for the standard OpenStreetMap tile server. It is meant for light
    use; for heavy production traffic use a tile provider (see README).

  props
    school : { latitude, longitude, radius, name }   (optional)
    points : [{ id, name, employeeId, date, time, kind: "Sign in" | "Sign out",
                latitude, longitude, distance }]
*/

export const isValidPoint = (lat, lon) => {
  const la = Number(lat);
  const lo = Number(lon);

  return (
    lat !== null &&
    lat !== undefined &&
    lon !== null &&
    lon !== undefined &&
    lat !== "" &&
    lon !== "" &&
    Number.isFinite(la) &&
    Number.isFinite(lo) &&
    la >= -90 &&
    la <= 90 &&
    lo >= -180 &&
    lo <= 180 &&
    !(la === 0 && lo === 0)
  );
};

const escapeHtml = (value) =>
  String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

const KIND_COLORS = { "Sign in": "#1d7a4d", "Sign out": "#c2410c" };

function AttendanceMap({ school, points = [], height = 380 }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const layerRef = useRef(null);

  const validPoints = points.filter((p) => isValidPoint(p.latitude, p.longitude));
  const skipped = points.length - validPoints.length;
  const schoolOk = school && isValidPoint(school.latitude, school.longitude);

  // create the map once
  useEffect(() => {
    const map = L.map(containerRef.current, { scrollWheelZoom: false });

    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
    }).addTo(map);

    layerRef.current = L.layerGroup().addTo(map);
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
      layerRef.current = null;
    };
  }, []);

  // (re)draw markers when the data changes
  useEffect(() => {
    const map = mapRef.current;
    const layer = layerRef.current;

    if (!map || !layer) return;

    layer.clearLayers();

    const bounds = [];

    if (schoolOk) {
      const center = [Number(school.latitude), Number(school.longitude)];

      L.marker(center, {
        icon: L.divIcon({
          className: "att-map-school-icon",
          html: "<span>🏫</span>",
          iconSize: [34, 34],
          iconAnchor: [17, 17],
        }),
        title: "School",
        zIndexOffset: 1000,
      })
        .bindPopup(
          `<strong>${escapeHtml(school.name || "School")}</strong><br/>School location` +
            (Number(school.radius) > 0
              ? `<br/>Allowed radius: ${escapeHtml(Math.round(Number(school.radius)))} m`
              : "")
        )
        .addTo(layer);

      if (Number(school.radius) > 0) {
        L.circle(center, {
          radius: Number(school.radius),
          color: "#2563eb",
          weight: 2,
          fillColor: "#2563eb",
          fillOpacity: 0.08,
          interactive: false,
        }).addTo(layer);
      }

      bounds.push(center);
    }

    validPoints.forEach((p) => {
      const position = [Number(p.latitude), Number(p.longitude)];
      const color = KIND_COLORS[p.kind] || "#1d7a4d";

      L.circleMarker(position, {
        radius: 9,
        color: "#ffffff",
        weight: 2,
        fillColor: color,
        fillOpacity: 0.95,
      })
        .bindPopup(
          `<strong>${escapeHtml(p.name || "Employee")}</strong>` +
            (p.employeeId ? ` (${escapeHtml(p.employeeId)})` : "") +
            `<br/>${escapeHtml(p.kind || "Attendance")}` +
            `<br/>Date: ${escapeHtml(p.date || "-")}` +
            `<br/>Time: ${escapeHtml(p.time || "-")}` +
            (p.distance !== null && p.distance !== undefined
              ? `<br/>Distance from school: ${escapeHtml(p.distance)} m`
              : "") +
            `<br/><small>${escapeHtml(Number(p.latitude).toFixed(6))}, ${escapeHtml(
              Number(p.longitude).toFixed(6)
            )}</small>`
        )
        .addTo(layer);

      bounds.push(position);
    });

    if (bounds.length === 1) {
      map.setView(bounds[0], 17);
    } else if (bounds.length > 1) {
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 18 });
    } else {
      map.setView([-6.1746, 39.2264], 12); // neutral view only; no marker is drawn
    }

    // the map is often created inside a modal that is still animating in
    setTimeout(() => map.invalidateSize(), 150);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [points, school]);

  return (
    <div className="att-map-wrap">
      <div
        ref={containerRef}
        className="att-map"
        style={{ height }}
        role="application"
        aria-label="Attendance map"
      />

      <div className="att-map-legend">
        {schoolOk && <span><i className="dot school" /> School (blue circle = allowed radius)</span>}
        <span><i className="dot in" /> Sign in</span>
        <span><i className="dot out" /> Sign out</span>
      </div>

      {!schoolOk && (
        <p className="att-map-note">
          The school location is not set or not valid, so it is not shown. Set it in Settings.
        </p>
      )}

      {validPoints.length === 0 && (
        <p className="att-map-note">No recorded location to show for this selection.</p>
      )}

      {skipped > 0 && (
        <p className="att-map-note">
          {skipped} record{skipped === 1 ? "" : "s"} could not be drawn because the saved
          coordinates are missing or invalid.
        </p>
      )}
    </div>
  );
}

export default AttendanceMap;

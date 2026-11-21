package com.developers_group.biometricattendance.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the same {"message": "..."} error shape the rest of the API uses, from filters. */
public final class JsonErrors {

    private JsonErrors() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        write(response, status, message, null);
    }

    public static void write(HttpServletResponse response, int status, String message, String code)
            throws IOException {

        if (response.isCommitted()) return;

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        StringBuilder json = new StringBuilder("{\"message\":\"").append(escape(message)).append('"');

        if (code != null) {
            json.append(",\"code\":\"").append(escape(code)).append('"');
        }

        json.append('}');

        response.getWriter().write(json.toString());
    }

    static String escape(String value) {
        if (value == null) return "";

        StringBuilder out = new StringBuilder(value.length() + 8);

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        return out.toString();
    }
}

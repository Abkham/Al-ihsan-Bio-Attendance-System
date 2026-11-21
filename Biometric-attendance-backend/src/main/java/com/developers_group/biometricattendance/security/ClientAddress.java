package com.developers_group.biometricattendance.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Network address used for throttling only. X-Forwarded-For is deliberately NOT trusted
 * (anyone can fake it). Behind a reverse proxy configure
 * server.forward-headers-strategy=native so getRemoteAddr() already is the real client.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}

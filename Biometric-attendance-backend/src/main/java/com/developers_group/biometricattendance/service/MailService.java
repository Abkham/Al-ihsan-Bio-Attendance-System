package com.developers_group.biometricattendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends e-mail through SMTP settings that come ONLY from environment variables
 * (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM).
 *
 * Sending happens on a background thread so the "forgot password" response takes the
 * same time whether or not an account exists (no account enumeration by timing).
 * Reset links are NEVER written to the log.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String host;
    private final String username;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mail-sender");
        t.setDaemon(true);
        return t;
    });

    public MailService(
            ObjectProvider<JavaMailSender> senderProvider,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username) {

        this.senderProvider = senderProvider;
        this.host = host;
        this.username = username;
        // MAIL_FROM is optional: most providers (Gmail, Outlook...) require the sender to be the
        // login address anyway, so fall back to MAIL_USERNAME.
        this.from = (from != null && !from.isBlank()) ? from.trim()
                : (username == null ? "" : username.trim());
    }

    @PostConstruct
    void reportStatus() {
        if (isConfigured()) {
            log.info("E-mail is ON: sending through {} as {}", host, from);
        } else {
            log.warn("E-mail is OFF. Fill MAIL_HOST, MAIL_USERNAME and MAIL_PASSWORD "
                    + "(see mail.properties.example) to send password-reset e-mails.");
        }
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank()
                && senderProvider.getIfAvailable() != null;
    }

    /** Fire-and-forget. Failures are logged without the message body. */
    public void sendAsync(String to, String subject, String body) {

        if (!isConfigured()) {
            log.warn("E-mail is not configured (set MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD). A message was NOT sent.");
            return;
        }

        executor.submit(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                senderProvider.getObject().send(message);
            } catch (Exception e) {
                // Reason only (e.g. authentication failed, connection timed out) - never the message body.
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                log.warn("E-mail could not be sent: {} - {}", e.getClass().getSimpleName(), root.getMessage());
            }
        });
    }
}

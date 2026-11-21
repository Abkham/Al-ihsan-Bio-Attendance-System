package com.developers_group.biometricattendance.config;

import com.developers_group.biometricattendance.security.PasswordHashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One-time, SAFE-TO-REPEAT data migration that runs on every start before the app serves requests.
 * Nothing here deletes employees or attendance history.
 *
 *  1. Plain-text passwords (old versions) -> BCrypt hashes. Already hashed values are left alone.
 *  2. Accounts that registered more than one device -> the NEWEST credential is kept, older ones
 *     are revoked (their history rows stay, marked "REPLACED").
 *  3. UNIQUE key on user_credentials.user_entity_user_id so the database itself enforces
 *     "one device per account".
 *  4. biometric.active_key is back-filled so the "one Active row per employee" key is in force.
 */
@Component
@Order(1)
public class StartupMigrations implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupMigrations.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public StartupMigrations(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        allowEmployeesWithoutAccount();
        hashLegacyPasswords("employee", "employee_id");
        hashLegacyPasswords("admin", "admin_id");
        enforceOneCredentialPerAccount();
        backfillActiveKeys();
    }

    // ---------------------------------------------------------------- 0

    /**
     * Employees now create their own username and password, so an employee row can exist without
     * them. Older databases declared both columns NOT NULL; relax that (nothing is deleted or changed).
     */
    private void allowEmployeesWithoutAccount() {

        try {
            List<Map<String, Object>> columns = jdbc.queryForList(
                    "SELECT column_name AS name, column_type AS type FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'employee' "
                            + "AND column_name IN ('username', 'password') AND is_nullable = 'NO'");

            for (Map<String, Object> column : columns) {

                String name = String.valueOf(column.get("name"));
                String type = String.valueOf(column.get("type"));

                // Both values come from the database catalogue, never from a user.
                if (!name.matches("username|password") || !type.matches("[A-Za-z0-9(), ]+")) continue;

                jdbc.execute("ALTER TABLE employee MODIFY `" + name + "` " + type + " NULL");
                log.info("Migration: employee.{} may now be empty until the employee registers.", name);
            }

        } catch (Exception e) {
            log.error("Could not relax employee.username/password to NULL ({}). Registering employees "
                    + "without a username will fail until this is fixed.", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 1

    private void hashLegacyPasswords(String table, String idColumn) {

        int converted = 0;

        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + idColumn + " AS id, password FROM " + table);

            for (Map<String, Object> row : rows) {

                String stored = (String) row.get("password");

                if (stored == null || PasswordHashing.isBcryptHash(stored)) continue;

                // BCrypt only reads 72 bytes; legacy passwords were short, but be safe.
                String hash = encoder.encode(stored.length() > 72 ? stored.substring(0, 72) : stored);

                jdbc.update("UPDATE " + table + " SET password = ? WHERE " + idColumn + " = ?", hash, row.get("id"));
                converted++;
            }
        } catch (Exception e) {
            log.error("Password migration for table {} failed: {}", table, e.getClass().getSimpleName());
            throw new IllegalStateException("Could not secure existing passwords. Start-up stopped.", e);
        }

        if (converted > 0) {
            log.info("Security migration: {} plain-text password(s) in '{}' were converted to BCrypt hashes.",
                    converted, table);
        }
    }

    // ---------------------------------------------------------------- 2 + 3

    private void enforceOneCredentialPerAccount() {

        try {
            List<Map<String, Object>> users = jdbc.queryForList(
                    "SELECT user_entity_user_id AS uid FROM user_credentials "
                            + "GROUP BY user_entity_user_id HAVING COUNT(*) > 1");

            for (Map<String, Object> user : users) {

                String uid = (String) user.get("uid");

                List<Map<String, Object>> credentials = jdbc.queryForList(
                        "SELECT credential_id FROM user_credentials WHERE user_entity_user_id = ? "
                                + "ORDER BY COALESCE(last_used, created) DESC, created DESC", uid);

                String keep = (String) credentials.get(0).get("credential_id");

                for (int i = 1; i < credentials.size(); i++) {

                    String old = (String) credentials.get(i).get("credential_id");

                    jdbc.update("DELETE FROM user_credentials WHERE credential_id = ?", old);

                    jdbc.update(
                            "UPDATE biometric SET status = 'Revoked', active_key = NULL, revoked_at = ?, "
                                    + "revoked_by = 'REPLACED', revoke_reason = 'Only one device per account is allowed' "
                                    + "WHERE fingerprint_id = ? AND status <> 'Revoked'",
                            Timestamp.from(Instant.now()), old);
                }

                log.info("Security migration: an account had {} devices; the newest was kept, others revoked.",
                        credentials.size());
            }

            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = 'user_credentials' AND index_name = 'uk_user_credentials_one_per_user'",
                    Integer.class);

            if (exists == null || exists == 0) {
                jdbc.execute("ALTER TABLE user_credentials ADD UNIQUE KEY uk_user_credentials_one_per_user "
                        + "(user_entity_user_id)");
                log.info("Security migration: UNIQUE key 'one device per account' added.");
            }

        } catch (Exception e) {
            // Without the key the application-level guard still works; say so loudly.
            log.error("Could not add the 'one device per account' database key: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 4

    private void backfillActiveKeys() {

        try {
            // Active mirror rows whose real credential no longer exists are not devices any more.
            jdbc.update(
                    "UPDATE biometric b SET b.status = 'Revoked', b.active_key = NULL, b.revoked_at = ?, "
                            + "b.revoked_by = 'REPLACED', b.revoke_reason = 'No matching credential on the server' "
                            + "WHERE b.status = 'Active' AND NOT EXISTS ("
                            + "SELECT 1 FROM user_credentials c WHERE c.credential_id = b.fingerprint_id)",
                    Timestamp.from(Instant.now()));

            // More than one Active row for the same employee: keep the newest.
            List<Map<String, Object>> employees = jdbc.queryForList(
                    "SELECT employee_id AS id FROM biometric WHERE status = 'Active' "
                            + "GROUP BY employee_id HAVING COUNT(*) > 1");

            for (Map<String, Object> e : employees) {
                jdbc.update(
                        "UPDATE biometric SET status = 'Revoked', active_key = NULL, revoked_at = ?, "
                                + "revoked_by = 'REPLACED', revoke_reason = 'Only one device per account is allowed' "
                                + "WHERE employee_id = ? AND status = 'Active' AND biometric_id <> ("
                                + "SELECT keep_id FROM (SELECT MAX(biometric_id) AS keep_id FROM biometric "
                                + "WHERE employee_id = ? AND status = 'Active') t)",
                        Timestamp.from(Instant.now()), e.get("id"), e.get("id"));
            }

            jdbc.update("UPDATE biometric SET active_key = employee_id WHERE status = 'Active' AND active_key IS NULL");

        } catch (Exception e) {
            log.error("Could not back-fill device keys: {}", e.getMessage());
        }
    }
}

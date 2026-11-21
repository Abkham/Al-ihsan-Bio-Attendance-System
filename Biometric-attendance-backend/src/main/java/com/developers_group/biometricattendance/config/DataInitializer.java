package com.developers_group.biometricattendance.config;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.SystemSetting;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Makes a FRESH database usable:
 *  - one administrator (only when the admin table is empty)
 *  - one default system-setting row (only when the settings table is empty)
 *
 * The first administrator's password is taken from INITIAL_ADMIN_PASSWORD. When it is not set,
 * a random password is generated and printed ONCE in the server console, because a
 * well-known default password must never exist on a real system. Existing databases are untouched.
 */
@Component
@Order(2)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminRepository adminRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;

    public DataInitializer(AdminRepository adminRepository,
                           SystemSettingRepository systemSettingRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${INITIAL_ADMIN_PASSWORD:}") String initialPassword) {
        this.adminRepository = adminRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(String... args) {

        if (adminRepository.count() == 0) {

            boolean generated = initialPassword == null || initialPassword.isBlank();
            String password = generated ? randomPassword() : initialPassword;

            Admin admin = new Admin();
            admin.setFullName("System Administrator");
            admin.setPosition("Administrator");
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(password));
            admin.setPasswordChangedAt(Instant.now());
            adminRepository.save(admin);

            if (generated) {
                System.out.println("==========================================================");
                System.out.println(" First administrator created.  Username: admin");
                System.out.println(" One-time password: " + password);
                System.out.println(" Sign in, then change it in Settings. It is shown only once.");
                System.out.println("==========================================================");
            } else {
                log.info("First administrator 'admin' created with the password from INITIAL_ADMIN_PASSWORD.");
            }
        }

        if (systemSettingRepository.count() == 0) {
            SystemSetting setting = new SystemSetting();
            setting.setSchoolName("Al-Ihsan Girls Secondary School");
            setting.setSchoolLatitude(-6.1746053);
            setting.setSchoolLongitude(39.2263778);
            setting.setAllowedRadius(100.0);
            setting.setWorkStartTime(LocalTime.of(8, 0));
            setting.setWorkEndTime(LocalTime.of(16, 0));
            systemSettingRepository.save(setting);
            log.info("Default system settings created (change them in Admin > Settings).");
        }
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.append('7').toString();
    }
}

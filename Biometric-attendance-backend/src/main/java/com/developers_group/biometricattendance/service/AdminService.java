package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.exception.ApiException;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final ProfilePhotoStorage photoStorage;

    public AdminService(
            AdminRepository adminRepository,
            PasswordService passwordService,
            ProfilePhotoStorage photoStorage) {

        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.photoStorage = photoStorage;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean sameIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Transactional
    public Admin createAdmin(Admin admin) {

        String username = NameRules.username(admin.getUsername());
        String email = NameRules.optionalEmail(admin.getEmail());
        String hash = passwordService.hashNew(admin.getPassword(), username);

        if (adminRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username already exists");
        }

        if (email != null && adminRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email already exists");
        }

        Admin created = new Admin();
        created.setUsername(username);
        created.setEmail(email);
        created.setFullName(NameRules.tidy(admin.getFullName()));
        created.setPosition(NameRules.tidy(admin.getPosition()));
        created.setContact(NameRules.tidy(admin.getContact()));
        created.setPassword(hash);
        created.setPasswordChangedAt(now());

        return adminRepository.save(created);
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public Optional<Admin> getAdminById(Long id) {
        return adminRepository.findById(id);
    }

    // =========================
    // UPDATE ADMIN PROFILE (name, position, contact, e-mail, USERNAME). Password is separate.
    // =========================

    @Transactional
    public Admin updateAdmin(Long id, Admin updated) {

        Admin existing = adminRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Admin not found"));

        if (!isBlank(updated.getUsername())) {

            String username = NameRules.username(updated.getUsername());

            if (!sameIgnoreCase(existing.getUsername(), username)
                    && adminRepository.existsByUsernameIgnoreCase(username)) {
                throw ApiException.conflict("This username is already taken. Please choose another one.");
            }

            existing.setUsername(username);
        }

        if (!isBlank(updated.getEmail())) {

            String email = NameRules.optionalEmail(updated.getEmail());

            if (!sameIgnoreCase(existing.getEmail(), email)
                    && adminRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.conflict("Email already exists");
            }

            existing.setEmail(email);
        }

        if (!isBlank(updated.getFullName())) existing.setFullName(NameRules.tidy(updated.getFullName()));
        if (updated.getPosition() != null) existing.setPosition(NameRules.tidy(updated.getPosition()));
        if (updated.getContact() != null) existing.setContact(NameRules.tidy(updated.getContact()));

        // profilePhoto is changed only through the upload endpoint.

        return adminRepository.save(existing);
    }

    @Transactional
    public void deleteAdmin(Long id) {

        if (!adminRepository.existsById(id)) {
            throw ApiException.notFound("Admin not found");
        }

        if (adminRepository.count() <= 1) {
            throw ApiException.badRequest("The last administrator cannot be deleted");
        }

        adminRepository.deleteById(id);
    }

    @Transactional
    public void changePassword(Long adminId, String currentPassword, String newPassword) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.notFound("Admin not found"));

        if (!passwordService.matches(currentPassword, admin.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }

        admin.setPassword(passwordService.hashNew(newPassword, admin.getUsername()));
        admin.setPasswordChangedAt(now());

        adminRepository.save(admin);
    }

    @Transactional
    public Admin uploadProfilePhoto(Long adminId, MultipartFile file) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.notFound("Admin not found"));

        admin.setProfilePhoto(photoStorage.save(
                ProfilePhotoStorage.Kind.ADMIN, "", file, admin.getProfilePhoto()));

        return adminRepository.save(admin);
    }
}

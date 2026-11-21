package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Saves profile photos to uploads/employee or uploads/admin.
 *
 * Every photo must have a blue background (see BlueBackgroundValidator).
 *
 * The file TYPE is decided by the file's first bytes ("magic numbers"), not by the
 * name or the Content-Type the browser claims, and the stored name is random.
 */
@Component
public class ProfilePhotoStorage {

    public static final long MAX_BYTES = 2L * 1024 * 1024;

    public enum Kind {
        EMPLOYEE("employee"), ADMIN("admin");

        final String folder;

        Kind(String folder) {
            this.folder = folder;
        }
    }

    /** @return extension (".jpg", ".png", ".webp", ".gif") or null when the bytes are not an allowed image. */
    static String detectExtension(byte[] b) {

        if (b == null || b.length < 12) return null;

        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return ".jpg";

        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return ".png";

        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return ".gif";

        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return ".webp";

        return null;
    }

    /** Stores the photo and returns its public path, e.g. /uploads/employee/EMP004-<uuid>.jpg */
    public String save(Kind kind, String owner, MultipartFile photo, String oldPhotoPath) {

        if (photo == null || photo.isEmpty()) {
            throw ApiException.badRequest("Please select a profile photo");
        }

        if (photo.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Profile photo must be 2MB or less");
        }

        try {

            byte[] bytes = photo.getBytes();

            String extension = detectExtension(bytes);

            if (extension == null) {
                throw ApiException.badRequest("Please choose a JPG, PNG or GIF image.");
            }

            // Blue-background rule: checked here on the server for every employee and
            // administrator upload, so it cannot be skipped by calling the API directly.
            BlueBackgroundValidator.Result background = BlueBackgroundValidator.check(bytes);

            if (background == BlueBackgroundValidator.Result.UNREADABLE) {
                throw ApiException.badRequest(
                        ".webp".equals(extension)
                                ? "WEBP photos cannot be checked for a blue background. Please upload a JPG or PNG image."
                                : "The photo could not be read. Please upload a different JPG or PNG image.");
            }

            if (background == BlueBackgroundValidator.Result.NOT_BLUE) {
                throw ApiException.badRequest(BlueBackgroundValidator.MESSAGE);
            }

            Path directory = Paths.get("uploads", kind.folder);
            Files.createDirectories(directory);

            String safeOwner = owner == null ? "" : owner.replaceAll("[^A-Za-z0-9_-]", "");
            String fileName = (safeOwner.isEmpty() ? "" : safeOwner + "-") + UUID.randomUUID() + extension;

            Files.write(directory.resolve(fileName), bytes);

            deleteIfStoredAsFile(kind, oldPhotoPath);

            return "/uploads/" + kind.folder + "/" + fileName;

        } catch (IOException e) {
            throw ApiException.badRequest("Failed to save profile photo");
        }
    }

    /** Removes an old photo file. Legacy Base64 values (not files) are simply dropped by the caller. */
    public void deleteIfStoredAsFile(Kind kind, String photoPath) {

        String prefix = "/uploads/" + kind.folder + "/";

        if (photoPath == null || !photoPath.startsWith(prefix)) return;

        String name = photoPath.substring(prefix.length());

        // never follow path tricks
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return;

        try {
            Files.deleteIfExists(Paths.get("uploads", kind.folder, name));
        } catch (Exception ignored) {
            // an undeletable old file must not fail the upload
        }
    }
}

package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.dto.SupportContactDTO;
import com.developers_group.biometricattendance.entity.Admin;
import com.developers_group.biometricattendance.entity.SystemSetting;
import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

/**
 * Where "contact the administrator" points. Order of preference:
 *   1. the support phone/e-mail saved by the administrator in Settings
 *   2. the first administrator's own profile contact / e-mail
 *   3. nothing (null) - the Help Center then tells the user the school has not set it yet
 */
@Service
public class SupportContactService {

    private final SystemSettingRepository settingRepository;
    private final AdminRepository adminRepository;

    public SupportContactService(SystemSettingRepository settingRepository, AdminRepository adminRepository) {
        this.settingRepository = settingRepository;
        this.adminRepository = adminRepository;
    }

    public SupportContactDTO get() {

        SystemSetting setting = settingRepository.findFirstByOrderBySettingIdAsc().orElse(null);
        Admin admin = adminRepository.findFirstByOrderByAdminIdAsc().orElse(null);

        String phone = firstNonBlank(setting == null ? null : setting.getSupportPhone(),
                admin == null ? null : admin.getContact());

        String email = firstNonBlank(setting == null ? null : setting.getSupportEmail(),
                admin == null ? null : admin.getEmail());

        return new SupportContactDTO(
                setting == null ? null : setting.getSchoolName(),
                phone,
                email,
                admin == null ? null : admin.getFullName(),
                setting == null || setting.getWorkStartTime() == null ? null : setting.getWorkStartTime().toString(),
                setting == null || setting.getWorkEndTime() == null ? null : setting.getWorkEndTime().toString());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }
}

package com.developers_group.biometricattendance.repository;

import com.developers_group.biometricattendance.entity.Biometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiometricRepository extends JpaRepository<Biometric, Long> {

    List<Biometric> findByEmployeeEmployeeId(String employeeId);

    List<Biometric> findByEmployeeEmployeeIdAndStatus(String employeeId, String status);

    boolean existsByEmployeeEmployeeIdAndFingerprintId(String employeeId, String fingerprintId);

    // Used when an employee is deleted
    void deleteByEmployeeEmployeeId(String employeeId);

    Optional<Biometric> findByFingerprintId(String fingerprintId);
}

package com.developers_group.biometricattendance.repository;

import com.developers_group.biometricattendance.entity.EmployeeNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeNotificationRepository extends JpaRepository<EmployeeNotification, Long> {

    List<EmployeeNotification> findByEmployeeEmployeeIdOrderByCreatedAtDescIdDesc(String employeeId, Pageable pageable);

    List<EmployeeNotification> findByEmployeeEmployeeIdAndReadAtIsNull(String employeeId);

    long countByEmployeeEmployeeIdAndReadAtIsNull(String employeeId);

    void deleteByEmployeeEmployeeId(String employeeId);
}

package com.developers_group.biometricattendance.repository;

import com.developers_group.biometricattendance.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<AuditLog> findByTargetEmployeeIdOrderByCreatedAtDescIdDesc(String targetEmployeeId, Pageable pageable);
}

package com.developers_group.biometricattendance.repository;

import com.developers_group.biometricattendance.entity.DeviceRemovalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRemovalRequestRepository extends JpaRepository<DeviceRemovalRequest, Long> {

    List<DeviceRemovalRequest> findAllByOrderByRequestedAtDesc();

    List<DeviceRemovalRequest> findByEmployeeEmployeeIdOrderByRequestedAtDesc(String employeeId);

    List<DeviceRemovalRequest> findByEmployeeEmployeeIdAndStatus(String employeeId, String status);

    // Row lock (SELECT ... FOR UPDATE). Always taken BEFORE the employee lock, so decisions on one request
    // are processed one at a time and two transactions can never wait on each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeviceRemovalRequest r where r.id = :id")
    Optional<DeviceRemovalRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOpenKey(String openKey);

    void deleteByEmployeeEmployeeId(String employeeId);
}

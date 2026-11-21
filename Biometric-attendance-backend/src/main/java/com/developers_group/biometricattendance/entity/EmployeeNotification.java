package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A message shown to an employee inside the system (for example "your old device was revoked"). */
@Entity
@Table(name = "employee_notifications", indexes = {
        @Index(name = "idx_notification_employee", columnList = "employee_id, created_at")
})
@Getter
@Setter
public class EmployeeNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_employee"))
    @JsonIgnore
    private Employee employee;

    @Column(length = 40)
    private String type;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(length = 1000, nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}

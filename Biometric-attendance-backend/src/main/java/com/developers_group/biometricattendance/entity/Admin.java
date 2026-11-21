package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "admin")
@Getter
@Setter
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    private String fullName;

    private String position;

    private String contact;

    @Column(unique = true)
    private String email;

    @Column(unique = true, nullable = false)
    private String username;

    /** BCrypt HASH. Accepted from requests (WRITE_ONLY) but NEVER serialised to responses, never logged. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Column(nullable = false)
    private String password;

    /** Sessions created before this moment are rejected (set on password change / reset). */
    @JsonIgnore
    private Instant passwordChangedAt;

    @ToString.Exclude
    @Column(length = 500)
    private String profilePhoto;
}

package com.developers_group.biometricattendance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;


@Entity
@Table(name = "employee")
@Getter
@Setter
public class Employee {

    /**
     * Employee ID is the PRIMARY KEY and a STRING, e.g. "EMP004".
     * It is typed by the administrator - it is NOT auto-generated.
     * Attendance and Biometric rows reference it with a VARCHAR foreign key.
     */
    @Id
    @Column(name = "employee_id", length = 50, nullable = false)
    private String employeeId;

    private String firstName;

    // Optional: not every employee has a middle name.
    private String middleName;

    private String lastName;

    @Column(unique = true)
    private String email;

    private String phone;

    private String department;

    private String position;

    private LocalDate hireDate;

    /**
     * Chosen by the EMPLOYEE when they create their own account (null until then).
     * UNIQUE still applies; MySQL allows several NULLs.
     */
    @Column(unique = true)
    private String username;

    /**
     * BCrypt HASH of the password (column name kept as "password" so existing
     * databases keep working - legacy plain-text values are converted to hashes
     * once at start-up, see StartupMigrations).
     * Accepted from requests (WRITE_ONLY) but NEVER serialised to responses, never logged.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Column
    private String password;

    /**
     * When the password last changed (reset, change, or set by an administrator).
     * Browser sessions created before this moment are rejected.
     */
    @JsonIgnore
    private Instant passwordChangedAt;

    private String status;

    /** BCrypt hash of the one-time activation code the administrator gives to the employee. */
    @JsonIgnore
    @ToString.Exclude
    @Column(length = 100)
    private String activationCodeHash;

    @JsonIgnore
    private Instant activationCodeExpiresAt;

    /**
     * BCrypt hash of the verification code the administrator hands over after an EMERGENCY device
     * revocation (stolen / lost phone). While it is set, a new device cannot be registered.
     */
    @JsonIgnore
    @ToString.Exclude
    @Column(length = 100)
    private String recoveryCodeHash;

    @JsonIgnore
    private Instant recoveryCodeExpiresAt;

    /** Shown ONCE to the administrator right after it is generated. Never stored in clear text. */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String activationCode;

    @ToString.Exclude
    @Column(columnDefinition = "LONGTEXT", nullable = true)
    private String profilePhoto;

    /** True once the employee has created their own username and password. */
    @JsonProperty("accountRegistered")
    public boolean isAccountRegistered() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    /** "First Middle Last" - the single place that decides how a name is displayed. */
    @JsonProperty("fullName")
    public String getFullName() {
        StringBuilder name = new StringBuilder();

        for (String part : new String[]{firstName, middleName, lastName}) {
            if (part != null && !part.trim().isEmpty()) {
                if (name.length() > 0) name.append(' ');
                name.append(part.trim());
            }
        }

        return name.toString();
    }

}

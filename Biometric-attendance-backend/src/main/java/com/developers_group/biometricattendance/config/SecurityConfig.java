package com.developers_group.biometricattendance.config;

import com.developers_group.biometricattendance.repository.AdminRepository;
import com.developers_group.biometricattendance.repository.EmployeeRepository;
import com.developers_group.biometricattendance.security.ApiRequestHeaderFilter;
import com.developers_group.biometricattendance.security.DeviceRegistrationGuardFilter;
import com.developers_group.biometricattendance.security.FingerprintVerificationFilter;
import com.developers_group.biometricattendance.security.JsonErrors;
import com.developers_group.biometricattendance.security.LoginAttemptLimiter;
import com.developers_group.biometricattendance.security.SessionRevocationFilter;
import com.developers_group.biometricattendance.service.DeviceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Passwords are stored as BCrypt hashes (work factor 12, random salt per password).
     * BCrypt is one-way: nobody - including administrators and database admins - can
     * turn a stored value back into the original password.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }

    /** Authenticated user is kept in the HTTP session (needed by the WebAuthn ceremonies too). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 5 wrong passwords in 15 minutes block that account / address for 15 minutes. */
    @Bean
    public LoginAttemptLimiter loginAttemptLimiter() {
        return new LoginAttemptLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
    }

    /** Per network address (a whole school can share one address, so the limit is generous). */
    @Bean
    public LoginAttemptLimiter addressLoginLimiter() {
        return new LoginAttemptLimiter(40, Duration.ofMinutes(15), Duration.ofMinutes(15));
    }

    /** Password-reset REQUESTS: 5 per hour per key (account, address). */
    @Bean
    public LoginAttemptLimiter resetRequestLimiter() {
        return new LoginAttemptLimiter(5, Duration.ofHours(1), Duration.ofHours(1));
    }

    /** Wrong reset tokens / tries per address. */
    @Bean
    public LoginAttemptLimiter resetConfirmLimiter() {
        return new LoginAttemptLimiter(10, Duration.ofMinutes(15), Duration.ofMinutes(15));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            EmployeeRepository employeeRepository,
            AdminRepository adminRepository,
            DeviceService deviceService,
            @Value("${app.webauthn.rp-id:localhost}") String rpId,
            @Value("${app.webauthn.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176}") String[] allowedOrigins)
            throws Exception {

        http
                .cors(cors -> {})

                // Cookie-session JSON API: forged cross-site requests are stopped by
                // SameSite cookies + the mandatory X-Requested-With header (see ApiRequestHeaderFilter).
                .csrf(csrf -> csrf.disable())

                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                JsonErrors.write(response, 401,
                                        "Please sign in to continue.", "NOT_AUTHENTICATED"))
                        .accessDeniedHandler((request, response, exception) ->
                                JsonErrors.write(response, 403,
                                        "You do not have permission to do that.", "FORBIDDEN")))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // ---- public ----
                        .requestMatchers(HttpMethod.POST, "/api/employees/login", "/api/admins/login").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/support/contact").permitAll()
                        // Photos have unguessable file names; they are shown on the login-protected pages only.
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webauthn/authenticate/options", "/login/webauthn").permitAll()

                        // ---- device registration ceremony: signed-in employees only ----
                        .requestMatchers("/webauthn/register", "/webauthn/register/**").hasRole("EMPLOYEE")

                        // ---- employee self-service (ownership is checked in the controllers) ----
                        .requestMatchers(HttpMethod.POST, "/api/employees/*/profile-photo").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.DELETE, "/api/employees/*/profile-photo").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/employees/*/change-password").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.PATCH, "/api/employees/*/profile").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/api/employees/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/attendance/check-in", "/api/attendance/check-out").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/employee/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/biometrics/employee/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/biometrics/employee/*").hasRole("EMPLOYEE")
                        .requestMatchers("/api/devices/employee/*/**").authenticated()
                        .requestMatchers("/api/device-requests/employee/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/notifications/employee/**").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/api/settings/current").authenticated()

                        // ---- administrators ----
                        .requestMatchers("/api/employees", "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers("/api/admins", "/api/admins/**").hasRole("ADMIN")
                        .requestMatchers("/api/attendance", "/api/attendance/**").hasRole("ADMIN")
                        .requestMatchers("/api/biometrics", "/api/biometrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/devices", "/api/devices/**").hasRole("ADMIN")
                        .requestMatchers("/api/device-requests", "/api/device-requests/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit-logs", "/api/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/settings", "/api/settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/dashboard", "/api/dashboard/**").hasRole("ADMIN")

                        // Anything not listed above still needs a signed-in user.
                        .anyRequest().authenticated())

                .webAuthn(webAuthn -> webAuthn
                        .rpName("Al-Ihsan Girls Secondary School")
                        .rpId(rpId)
                        .allowedOrigins(allowedOrigins));

        // Runs right after CORS (so CORS headers stay on JSON errors) and before authentication.
        http.addFilterAfter(new ApiRequestHeaderFilter(), CorsFilter.class);
        http.addFilterAfter(
                new SessionRevocationFilter(employeeRepository, adminRepository),
                org.springframework.security.web.context.SecurityContextHolderFilter.class);
        http.addFilterAfter(
                new DeviceRegistrationGuardFilter(employeeRepository, deviceService),
                SessionRevocationFilter.class);
        // After a successful fingerprint sign-in (POST /login/webauthn) remember it in the session;
        // check-in / check-out refuse to run without it.
        http.addFilterAfter(
                new FingerprintVerificationFilter(),
                DeviceRegistrationGuardFilter.class);

        return http.build();
    }
}

package com.solvit.internship_system.config;

import com.solvit.internship_system.entity.HrApprovalStatus;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    @Value("${app.init-data:false}")
    private boolean initData;

    private static final String ADMIN_EMAIL = "dididavid129@gmail.com";
    private static final String DEFAULT_PASSWORD = "Password123!";

    @Bean
    public CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            if (!initData) return;
            String pwd = encoder.encode(DEFAULT_PASSWORD);

            // Always ensure admin exists (create or reset password if already there)
            userRepository.findByEmail(ADMIN_EMAIL).ifPresentOrElse(
                existing -> {
                    existing.setPasswordHash(pwd);
                    existing.setActive(true);
                    existing.setHrApprovalStatus(HrApprovalStatus.APPROVED);
                    existing.setRole(Role.ADMIN);
                    userRepository.save(existing);
                    log.info("Admin user {} updated. Password reset to: {}", ADMIN_EMAIL, DEFAULT_PASSWORD);
                },
                () -> {
                    userRepository.save(User.builder().email(ADMIN_EMAIL).passwordHash(pwd)
                            .firstName("Admin").lastName("SOLVIT").role(Role.ADMIN)
                            .emailVerified(true).profileCompleted(true).active(true)
                            .hrApprovalStatus(HrApprovalStatus.APPROVED).build());
                    log.info("Admin user {} created. Password: {}", ADMIN_EMAIL, DEFAULT_PASSWORD);
                }
            );

            // Create intern and supervisor if they don't exist
            if (!userRepository.existsByEmail("intern@solvit.local")) {
                userRepository.save(User.builder().email("intern@solvit.local").passwordHash(pwd)
                        .firstName("Intern").lastName("User").role(Role.INTERN).emailVerified(true).profileCompleted(false).active(true)
                        .hrApprovalStatus(HrApprovalStatus.APPROVED).build());
            }
            if (!userRepository.existsByEmail("supervisor@solvit.local")) {
                userRepository.save(User.builder().email("supervisor@solvit.local").passwordHash(pwd)
                        .firstName("Supervisor").lastName("User").role(Role.SUPERVISOR).emailVerified(true).profileCompleted(true).active(true)
                        .hrApprovalStatus(HrApprovalStatus.APPROVED).build());
            }
            if (!userRepository.existsByEmail("hr@solvit.local")) {
                userRepository.save(User.builder().email("hr@solvit.local").passwordHash(pwd)
                        .firstName("HR").lastName("SOLVIT").role(Role.HR).emailVerified(true).profileCompleted(true).active(true)
                        .hrApprovalStatus(HrApprovalStatus.APPROVED).build());
                log.info("HR user hr@solvit.local created. Password: {}", DEFAULT_PASSWORD);
            }

            log.info("Login with {} / {} (Admin) or intern@solvit.local / supervisor@solvit.local / hr@solvit.local", ADMIN_EMAIL, DEFAULT_PASSWORD);
        };
    }
}

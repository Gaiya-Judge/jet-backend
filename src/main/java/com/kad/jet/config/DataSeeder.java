package com.kad.jet.config;

import com.kad.jet.config.model.SystemConfig;
import com.kad.jet.config.repository.SystemConfigRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigRepository systemConfigRepository;

    @Value("${admin.email}")
    private String superAdminEmail;

    @Value("${admin.password}")
    private String superAdminPassword;

    @Override
    public void run(String... args) throws Exception {
        seedSuperAdmin();
        seedSystemConfig();
    }

    private void seedSuperAdmin() {
        if (userRepository.count() == 0) {

            _User superAdmin = _User.builder()
                    .email("admin@jet.com")
                    .fullName("Super Admin")
                    .password(passwordEncoder
                            .encode("Admin@1234"))
                    .userRole(UserRole.SUPER_ADMIN)
                    .isActive(true)
                    .isBanned(false)
                    .build();

            userRepository.save(superAdmin);

            log.info("================================================");
            log.info("Super Admin seeded successfully");
            log.info("Email   : admin1@jet.com");
            log.info("Password: Admin@1234");
            log.info("Change this password immediately in production");
            log.info("================================================");
        }
    }

    private void seedSystemConfig() {
        if (systemConfigRepository.count() == 0) {

            // Maximum credit units a student can register
            // per semester — enforced at registration submit
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey(SystemConfigKeys.MAX_CREDIT_UNITS)
                    .configValue("18")
                    .description("Maximum credit units a student " +
                            "can register per semester")
                    .build());

            // Minimum credit units — prevents under-loading
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey(SystemConfigKeys.MIN_CREDIT_UNITS)
                    .configValue("2")
                    .description("Minimum credit units a student " +
                            "must register per semester")
                    .build());

            // 70% minimum payment threshold
            // Student must pay this before registration completes
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey(
                            SystemConfigKeys.MIN_PAYMENT_PERCENTAGE)
                    .configValue("70")
                    .description(
                            "Minimum percentage of total tuition " +
                                    "that must be paid before registration " +
                                    "is marked COMPLETED")
                    .build());

            log.info("System configuration seeded successfully");
        }
    }
}
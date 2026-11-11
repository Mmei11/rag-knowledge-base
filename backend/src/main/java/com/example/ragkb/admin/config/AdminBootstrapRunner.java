package com.example.ragkb.admin.config;

import com.example.ragkb.user.entity.UserRole;
import com.example.ragkb.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final String bootstrapUsername;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            @Value("${app.admin.bootstrap-username:}") String bootstrapUsername) {
        this.userRepository = userRepository;
        this.bootstrapUsername = bootstrapUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()) {
            return;
        }
        String username = bootstrapUsername.trim();
        userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    if (user.getRole() != UserRole.SUPER_ADMIN) {
                        user.changeRole(UserRole.SUPER_ADMIN);
                        userRepository.save(user);
                        log.info("Promoted bootstrap super admin, username={}", user.getUsername());
                    }
                },
                () -> log.warn("Bootstrap admin user not found, username={}", username));
    }
}

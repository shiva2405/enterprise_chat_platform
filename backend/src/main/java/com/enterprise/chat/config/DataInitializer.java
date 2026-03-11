package com.enterprise.chat.config;

import com.enterprise.chat.model.User;
import com.enterprise.chat.model.UserRole;
import com.enterprise.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .role(UserRole.ADMIN)
                    .active(true)
                    .available(true)
                    .build();
            userRepository.save(admin);
            log.info("Default admin account created: admin/admin123");
        }
        
        if (!userRepository.existsByUsername("agent1")) {
            User agent = User.builder()
                    .username("agent1")
                    .password(passwordEncoder.encode("agent123"))
                    .fullName("Demo Agent")
                    .role(UserRole.AGENT)
                    .active(true)
                    .available(true)
                    .build();
            userRepository.save(agent);
            log.info("Demo agent account created: agent1/agent123");
        }
    }
}

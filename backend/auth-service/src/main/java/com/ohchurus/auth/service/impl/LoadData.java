package com.ohchurus.auth.service.impl;

import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoadData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-data-enabled:true}")
    private boolean seedDataEnabled;

    // Credenciales de datos semilla externalizadas (sobre-escribibles por variables de entorno).
    // En produccion DEBEN definirse via SEED_*_PASSWORD; los valores por defecto son solo para demo local.
    @Value("${app.seed.admin-password:${SEED_ADMIN_PASSWORD:Admin123!}}")
    private String adminPassword;

    @Value("${app.seed.demo-password:${SEED_DEMO_PASSWORD:Demo123!}}")
    private String demoPassword;

    @Value("${app.seed.anderson-password:${SEED_ANDERSON_PASSWORD:Admin123!}}")
    private String andersonPassword;

    @Value("${app.seed.samy-password:${SEED_SAMY_PASSWORD:Samy123!}}")
    private String samyPassword;

    public LoadData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (seedDataEnabled) {
            seedUsers();
        }
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already exist, skipping seed data");
            return;
        }

        User admin = User.builder()
                .name("Administrador")
                .email("admin@ohchurus.com")
                .password(passwordEncoder.encode(adminPassword))
                .budgetStartDay(1)
                .active(true)
                .build();

        User demo = User.builder()
                .name("Usuario Demo")
                .email("demo@ohchurus.com")
                .password(passwordEncoder.encode(demoPassword))
                .budgetStartDay(1)
                .active(true)
                .build();

        User anderson = User.builder()
                .name("Anderson")
                .email("anderson@ohchurus.com")
                .password(passwordEncoder.encode(andersonPassword))
                .budgetStartDay(28)
                .active(true)
                .build();

        User samy = User.builder()
                .name("Samy")
                .email("samy@ohchurus.com")
                .password(passwordEncoder.encode(samyPassword))
                .budgetStartDay(28)
                .active(true)
                .build();

        userRepository.save(admin);
        userRepository.save(demo);
        userRepository.save(anderson);
        userRepository.save(samy);

        log.info("Seed data loaded: 4 users created (admin, demo, anderson, samy)");
    }
}

package com.ohchurus.auth.service.impl;

import com.ohchurus.auth.entity.Role;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.RoleRepository;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class LoadData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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

    public LoadData(UserRepository userRepository, RoleRepository roleRepository,
                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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

        /* Los dos roles los crea la migracion V3, que corre antes que esto.
           Si no estuvieran —una base a medio migrar— la semilla sigue adelante
           creando las cuentas sin rol en vez de tumbar el arranque: una cuenta
           sin rol se arregla desde la pantalla de administracion, un servicio
           que no arranca no se arregla desde ningun sitio. */
        Set<Role> deAdmin = rolesLlamados(SecurityUtils.ROL_ADMIN, SecurityUtils.ROL_USER);
        Set<Role> deUsuario = rolesLlamados(SecurityUtils.ROL_USER);

        User admin = User.builder()
                .name("Administrador")
                .email("admin@ohchurus.com")
                .password(passwordEncoder.encode(adminPassword))
                .budgetStartDay(1)
                .active(true)
                .roles(new HashSet<>(deAdmin))
                .build();

        User demo = User.builder()
                .name("Usuario Demo")
                .email("demo@ohchurus.com")
                .password(passwordEncoder.encode(demoPassword))
                .budgetStartDay(1)
                .active(true)
                .roles(new HashSet<>(deUsuario))
                .build();

        User anderson = User.builder()
                .name("Anderson")
                .email("anderson@ohchurus.com")
                .password(passwordEncoder.encode(andersonPassword))
                .budgetStartDay(28)
                .active(true)
                .roles(new HashSet<>(deAdmin))
                .build();

        User samy = User.builder()
                .name("Samy")
                .email("samy@ohchurus.com")
                .password(passwordEncoder.encode(samyPassword))
                .budgetStartDay(28)
                .active(true)
                .roles(new HashSet<>(deUsuario))
                .build();

        userRepository.save(admin);
        userRepository.save(demo);
        userRepository.save(anderson);
        userRepository.save(samy);

        log.info("Seed data loaded: 4 users created (admin, demo, anderson, samy)");
    }

    private Set<Role> rolesLlamados(String... nombres) {
        Set<Role> encontrados = new HashSet<>();
        for (String nombre : nombres) {
            roleRepository.findByNameAndActiveTrue(nombre).ifPresentOrElse(
                    encontrados::add,
                    () -> log.warn("La semilla no encontro el rol {}; las cuentas se crean sin el", nombre));
        }
        return encontrados;
    }
}

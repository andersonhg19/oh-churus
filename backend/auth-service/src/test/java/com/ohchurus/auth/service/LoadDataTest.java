package com.ohchurus.auth.service;

import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.service.impl.LoadData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadDataTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.ohchurus.auth.repository.RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LoadData loadData;

    @Test
    @DisplayName("Should seed users when database is empty")
    void shouldSeedUsersWhenEmpty() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        ReflectionTestUtils.setField(loadData, "adminPassword", "Admin123!");
        ReflectionTestUtils.setField(loadData, "demoPassword", "Demo123!");
        ReflectionTestUtils.setField(loadData, "andersonPassword", "Admin123!");
        ReflectionTestUtils.setField(loadData, "samyPassword", "Samy123!");
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.findByNameAndActiveTrue(anyString()))
                .thenReturn(java.util.Optional.of(
                        com.ohchurus.auth.entity.Role.builder().id(1L).name("USER").active(true).build()));

        loadData.run();

        verify(userRepository, atLeast(2)).save(any(User.class));
    }

    @Test
    @DisplayName("si la migracion no dejo los roles, la semilla sigue en vez de tumbar el arranque")
    void sinRolesLaSemillaNoRompe() {
        /* Un servicio que no arranca no se arregla desde ninguna pantalla; una
           cuenta sin rol si. Por eso la ausencia del rol es un aviso en el log
           y no una excepcion. */
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        ReflectionTestUtils.setField(loadData, "adminPassword", "Admin123!");
        ReflectionTestUtils.setField(loadData, "demoPassword", "Demo123!");
        ReflectionTestUtils.setField(loadData, "andersonPassword", "Admin123!");
        ReflectionTestUtils.setField(loadData, "samyPassword", "Samy123!");
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.findByNameAndActiveTrue(anyString())).thenReturn(java.util.Optional.empty());

        loadData.run();

        verify(userRepository, atLeast(4)).save(any(User.class));
    }

    @Test
    @DisplayName("Should skip seed when users exist")
    void shouldSkipWhenUsersExist() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", true);
        when(userRepository.count()).thenReturn(2L);

        loadData.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should skip seed when disabled")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(loadData, "seedDataEnabled", false);

        loadData.run();

        verify(userRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }
}

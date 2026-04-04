package com.ohchurus.auth.security;

import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MyUserDetailsService myUserDetailsService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@ohchurus.com")
                .password("encoded-password")
                .budgetStartDay(1)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("Should return UserDetails for valid active user")
        void shouldReturnUserDetailsForValidActiveUser() {
            when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com"))
                    .thenReturn(Optional.of(activeUser));

            UserDetails userDetails = myUserDetailsService.loadUserByUsername("test@ohchurus.com");

            assertNotNull(userDetails);
            assertEquals("test@ohchurus.com", userDetails.getUsername());
            assertEquals("encoded-password", userDetails.getPassword());
            assertTrue(userDetails.isEnabled());
            assertTrue(userDetails.isAccountNonExpired());
            assertTrue(userDetails.isAccountNonLocked());
            assertTrue(userDetails.isCredentialsNonExpired());
            verify(userRepository).findByEmailAndActiveTrue("test@ohchurus.com");
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for non-existent email")
        void shouldThrowExceptionForNonExistentEmail() {
            when(userRepository.findByEmailAndActiveTrue("nonexistent@ohchurus.com"))
                    .thenReturn(Optional.empty());

            UsernameNotFoundException exception = assertThrows(
                    UsernameNotFoundException.class,
                    () -> myUserDetailsService.loadUserByUsername("nonexistent@ohchurus.com")
            );

            assertTrue(exception.getMessage().contains("nonexistent@ohchurus.com"));
            verify(userRepository).findByEmailAndActiveTrue("nonexistent@ohchurus.com");
        }

        @Test
        @DisplayName("Should throw UsernameNotFoundException for inactive user")
        void shouldThrowExceptionForInactiveUser() {
            // findByEmailAndActiveTrue returns empty for inactive users
            when(userRepository.findByEmailAndActiveTrue("inactive@ohchurus.com"))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UsernameNotFoundException.class,
                    () -> myUserDetailsService.loadUserByUsername("inactive@ohchurus.com")
            );

            verify(userRepository).findByEmailAndActiveTrue("inactive@ohchurus.com");
        }

        @Test
        @DisplayName("Should return UserDetails with correct email as username")
        void shouldReturnUserDetailsWithCorrectEmail() {
            when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com"))
                    .thenReturn(Optional.of(activeUser));

            UserDetails userDetails = myUserDetailsService.loadUserByUsername("test@ohchurus.com");

            assertEquals(activeUser.getEmail(), userDetails.getUsername());
        }
    }
}

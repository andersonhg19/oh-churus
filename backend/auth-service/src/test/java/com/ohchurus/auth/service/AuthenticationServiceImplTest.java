package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.security.SecParams;
import com.ohchurus.auth.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Nested;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecParams secParams;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private User testUser;
    private AuthenticationRequest request;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@ohchurus.com")
                .password("encoded-password")
                .budgetStartDay(1)
                .active(true)
                .build();

        request = new AuthenticationRequest();
        request.setEmail("test@ohchurus.com");
        request.setPassword("Password123!");
    }

    @Test
    @DisplayName("Should authenticate successfully and return JWT")
    void shouldAuthenticateSuccessfully() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@ohchurus.com", null));
        when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(Optional.of(testUser));
        when(secParams.getSecret()).thenReturn("test-secret-key");
        when(secParams.getExpiredTime()).thenReturn(3600000L);

        AuthenticationResponse response = authenticationService.authenticate(request);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("test@ohchurus.com", response.getEmail());
        assertEquals("Test User", response.getName());
        assertEquals(1L, response.getUserId());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException for invalid password")
    void shouldThrowBadCredentialsForInvalidPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.authenticate(request));
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when user not found after auth")
    void shouldThrowWhenUserNotFoundAfterAuth() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@ohchurus.com", null));
        when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.authenticate(request));
    }

    @Test
    @DisplayName("Should include userId and name in JWT claims")
    void shouldIncludeClaimsInJWT() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(Optional.of(testUser));
        when(secParams.getSecret()).thenReturn("test-secret-key");
        when(secParams.getExpiredTime()).thenReturn(3600000L);

        AuthenticationResponse response = authenticationService.authenticate(request);

        assertNotNull(response.getToken());
        assertTrue(response.getToken().split("\\.").length == 3); // JWT has 3 parts
    }

    @Nested
    @DisplayName("JWT Token Claims")
    class JwtClaimsTests {

        @Test
        @DisplayName("Should generate JWT with correct subject (email) and claims (userId, name)")
        void shouldGenerateJwtWithCorrectClaims() {
            when(authenticationManager.authenticate(any())).thenReturn(null);
            when(userRepository.findByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(Optional.of(testUser));
            when(secParams.getSecret()).thenReturn("test-secret-key");
            when(secParams.getExpiredTime()).thenReturn(3600000L);

            AuthenticationResponse response = authenticationService.authenticate(request);

            DecodedJWT decoded = JWT.decode(response.getToken());
            assertEquals("test@ohchurus.com", decoded.getSubject());
            assertEquals(1L, decoded.getClaim("userId").asLong());
            assertEquals("Test User", decoded.getClaim("name").asString());
            assertNotNull(decoded.getExpiresAt());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should throw BadCredentialsException when email is null in request")
        void shouldThrowWhenEmailIsNull() {
            AuthenticationRequest nullEmailRequest = new AuthenticationRequest();
            nullEmailRequest.setEmail(null);
            nullEmailRequest.setPassword("Password123!");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class,
                    () -> authenticationService.authenticate(nullEmailRequest));
        }

        @Test
        @DisplayName("Should throw BadCredentialsException when password is empty")
        void shouldThrowWhenPasswordIsEmpty() {
            AuthenticationRequest emptyPassRequest = new AuthenticationRequest();
            emptyPassRequest.setEmail("test@ohchurus.com");
            emptyPassRequest.setPassword("");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class,
                    () -> authenticationService.authenticate(emptyPassRequest));
        }
    }
}

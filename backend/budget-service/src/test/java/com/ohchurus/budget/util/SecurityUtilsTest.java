package com.ohchurus.budget.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @Test
    @DisplayName("Should return authenticated email from SecurityContext")
    void shouldReturnAuthenticatedEmail() {
        String expectedEmail = "test@ohchurus.com";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(expectedEmail, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = SecurityUtils.getAuthenticatedEmail();

        assertEquals(expectedEmail, result);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should throw when no authentication present")
    void shouldThrowWhenNoAuth() {
        SecurityContextHolder.clearContext();

        assertThrows(NullPointerException.class, SecurityUtils::getAuthenticatedEmail);
    }
}

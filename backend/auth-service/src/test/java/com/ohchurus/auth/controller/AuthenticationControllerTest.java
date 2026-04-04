package com.ohchurus.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.security.JWTAuthorizationFilter;
import com.ohchurus.auth.security.SecParams;
import com.ohchurus.auth.service.AuthenticationService;
import com.ohchurus.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private UserService userService;

    @MockBean
    private JWTAuthorizationFilter jwtAuthorizationFilter;

    @MockBean
    private SecParams secParams;

    @Test
    @DisplayName("POST /v1/auth/login - Should return token on valid login")
    void loginSuccess() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("test@ohchurus.com");
        request.setPassword("Password123!");

        AuthenticationResponse authResponse = new AuthenticationResponse();
        authResponse.setToken("jwt-token");
        authResponse.setEmail("test@ohchurus.com");
        authResponse.setName("Test");
        authResponse.setUserId(1L);

        when(authenticationService.authenticate(any())).thenReturn(authResponse);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.object.token").value("jwt-token"))
                .andExpect(jsonPath("$.object.email").value("test@ohchurus.com"));
    }

    @Test
    @DisplayName("POST /v1/auth/login - Should return error on invalid credentials")
    void loginFailure() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("test@ohchurus.com");
        request.setPassword("wrong");

        when(authenticationService.authenticate(any())).thenThrow(new BadCredentialsException("Invalid"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    @DisplayName("POST /v1/auth/register - Should register user successfully")
    void registerSuccess() throws Exception {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setName("New User");
        dto.setEmail("new@ohchurus.com");
        dto.setPassword("Password123!");

        when(userService.saveAndUpdate(any())).thenReturn(new ResultDTO(true, "OK", 0));

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    @DisplayName("POST /v1/auth/register - Should return 400 when name is missing")
    void registerMissingName() throws Exception {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setEmail("new@ohchurus.com");
        dto.setPassword("Password123!");
        // name is null -> @NotBlank should fail

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/auth/register - Should return 400 for invalid email format")
    void registerInvalidEmailFormat() throws Exception {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setName("New User");
        dto.setEmail("not-an-email");
        dto.setPassword("Password123!");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}

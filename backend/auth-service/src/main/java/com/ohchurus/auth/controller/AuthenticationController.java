package com.ohchurus.auth.controller;

import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.service.AuthenticationService;
import com.ohchurus.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    public AuthenticationController(AuthenticationService authenticationService, UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @PostMapping(value = "/login", produces = "application/json")
    public ResponseEntity<ResultDTO> login(@Valid @RequestBody AuthenticationRequest request) {
        try {
            AuthenticationResponse response = authenticationService.authenticate(request);
            return ResponseEntity.ok(new ResultDTO(response));
        } catch (BadCredentialsException e) {
            return ResponseEntity.ok(new ResultDTO(false, "Invalid email or password", 101));
        }
    }

    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity<ResultDTO> register(@Valid @RequestBody UserSaveDTO request) {
        ResultDTO result = userService.saveAndUpdate(request);
        return ResponseEntity.ok(result);
    }
}

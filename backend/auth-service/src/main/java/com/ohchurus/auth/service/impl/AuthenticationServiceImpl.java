package com.ohchurus.auth.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.security.SecParams;
import com.ohchurus.auth.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final SecParams secParams;

    public AuthenticationServiceImpl(AuthenticationManager authenticationManager,
                                     UserRepository userRepository,
                                     SecParams secParams) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.secParams = secParams;
    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            log.warn("Failed authentication attempt for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailAndActiveTrue(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String jwt = JWT.create()
                .withSubject(user.getEmail())
                .withClaim("userId", user.getId())
                .withClaim("name", user.getName())
                .withExpiresAt(new Date(System.currentTimeMillis() + secParams.getExpiredTime()))
                .sign(Algorithm.HMAC256(secParams.getSecret()));

        log.info("User authenticated: {}", user.getEmail());

        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken(jwt);
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setUserId(user.getId());
        return response;
    }
}

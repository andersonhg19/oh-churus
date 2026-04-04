package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;

public interface AuthenticationService {

    AuthenticationResponse authenticate(AuthenticationRequest request);
}

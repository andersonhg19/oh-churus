package com.ohchurus.auth.controller;

import com.ohchurus.auth.dto.input.AuthenticationRequest;
import com.ohchurus.auth.dto.input.AuthenticationResponse;
import com.ohchurus.auth.dto.input.UserRegisterDTO;
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

    /**
     * Alta publica.
     *
     * Dos arreglos en el mismo sitio:
     *
     * 1. SEGURIDAD: recibe UserRegisterDTO, que no tiene campo "id". Antes
     *    recibia UserSaveDTO y llamaba a saveAndUpdate, asi que enviando un id
     *    se ACTUALIZABA a otro usuario —sin token, porque esta ruta es
     *    publica— cambiandole correo y contrasena.
     *
     * 2. FLUJO: devuelve lo mismo que /login (token incluido). El frontend ya
     *    esperaba eso (AuthContext.register lee authData.token), pero el
     *    backend devolvia el usuario sin token, asi que registrarse dejaba a
     *    la persona con una sesion vacia.
     */
    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity<ResultDTO> register(@Valid @RequestBody UserRegisterDTO request) {
        ResultDTO creado = userService.register(request);
        if (!creado.isCorrect()) {
            return ResponseEntity.ok(creado);
        }
        AuthenticationResponse sesion = authenticationService.authenticate(
                new AuthenticationRequest(request.getEmail(), request.getPassword()));
        return ResponseEntity.ok(new ResultDTO(sesion));
    }
}

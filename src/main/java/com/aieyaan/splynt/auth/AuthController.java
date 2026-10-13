package com.aieyaan.splynt.auth;

import com.aieyaan.splynt.auth.dto.LoginRequest;
import com.aieyaan.splynt.auth.dto.LoginResponse;
import com.aieyaan.splynt.auth.dto.RegisterRequest;
import com.aieyaan.splynt.auth.dto.RegisterResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.aieyaan.splynt.security.AuthenticationLimits limits;

    public AuthController(AuthService authService, com.aieyaan.splynt.security.AuthenticationLimits limits) {
        this.limits=limits;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response =
                authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest servlet
    ) {
        jakarta.servlet.ServletRequest peer=servlet;
        while(peer instanceof jakarta.servlet.ServletRequestWrapper wrapper) peer=wrapper.getRequest();
        limits.check("login-ip",peer.getRemoteAddr(),60,15);
        limits.check("login-account",request.email().trim().toLowerCase(java.util.Locale.ROOT),10,15);
        return ResponseEntity.ok(
                authService.login(request)
        );
    }
}
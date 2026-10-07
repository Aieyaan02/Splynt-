package com.aieyaan.splynt.account;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.aieyaan.splynt.auth.dto.ChangePasswordRequest;

@RestController
@RequestMapping("/api/me/password")
public class PasswordChangeController {
    private final PasswordChangeService service;
    public PasswordChangeController(PasswordChangeService service) { this.service = service; }
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        service.change(jwt, request);
    }
}

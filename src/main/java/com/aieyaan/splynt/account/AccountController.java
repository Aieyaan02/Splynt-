package com.aieyaan.splynt.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieyaan.splynt.account.dto.AccountResponse;

@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse getCurrentAccount(
            @AuthenticationPrincipal Jwt jwt) {

        return accountService.getCurrentAccount(
                jwt.getSubject()
        );
    }
}
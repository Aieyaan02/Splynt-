package com.aieyaan.splynt.auth.recovery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.aieyaan.splynt.auth.validation.PasswordByteLimit;
import static com.aieyaan.splynt.auth.recovery.AccountActionToken.Purpose.*;

@RestController @RequestMapping("/api/auth")
public class AccountRecoveryController {
    private final AccountEmailRequestService requests;
    private final AccountActionService actions;
    private final AccountEmailDelivery delivery;
    public AccountRecoveryController(AccountEmailRequestService requests, AccountActionService actions, AccountEmailDelivery delivery) {
        this.requests = requests; this.actions = actions; this.delivery = delivery;
    }
    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) { }
    public record TokenRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) { }
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 10, max = 72) @PasswordByteLimit String password) { }
    @GetMapping("/recovery/status") public Map<String, Boolean> status() { return Map.of("available", delivery.configured()); }
    @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestReset(@Valid @RequestBody EmailRequest request) {
        requests.request(request.email(), RESET_PASSWORD); return accepted();
    }
    @PostMapping("/email-verification/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestVerification(@Valid @RequestBody EmailRequest request) {
        requests.request(request.email(), VERIFY_EMAIL); return accepted();
    }
    @PostMapping("/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) { actions.resetPassword(request.token(), request.password()); }
    @PostMapping("/email-verification/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody TokenRequest request) { actions.verifyEmail(request.token()); }
    private Map<String, String> accepted() {
        return Map.of("message", "If this address is eligible, an email will arrive shortly. Check your spam folder. If you have requested several emails, wait before trying again.");
    }
}

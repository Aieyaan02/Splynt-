package com.aieyaan.splynt.clover;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CloverOAuthController {
    private static final String BINDING_COOKIE = "splynt_clover_session";
    private static final String CALLBACK = "/api/integrations/clover/connect";
    private final CloverConnectionService connections;

    public CloverOAuthController(CloverConnectionService connections) { this.connections = connections; }

    @PostMapping("/api/stores/{storeId}/integrations/clover/connect")
    @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public ResponseEntity<Map<String, String>> begin(@PathVariable Long storeId,
            Authentication authentication, HttpServletRequest request) {
        String binding = CloverOAuthStateService.randomValue();
        String url = connections.begin(storeId, Long.valueOf(authentication.getName()), binding);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(binding, request.isSecure(), Duration.ofMinutes(10)))
                .body(Map.of("authorizationUrl", url));
    }

    @GetMapping("/api/stores/{storeId}/integrations/clover")
    @PreAuthorize("@storeAuthorizationService.canAccess(authentication, #storeId)")
    public CloverConnectionService.ConnectionStatus status(@PathVariable Long storeId) {
        return connections.status(storeId);
    }

    @GetMapping(CALLBACK)
    public ResponseEntity<Void> complete(@RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(name = "merchant_id", required = false) String merchantId,
            @CookieValue(name = BINDING_COOKIE, required = false) String binding,
            HttpServletRequest request) {
        String destination;
        try {
            Long storeId = connections.complete(state, binding, code, merchantId);
            destination = "/?clover=connected&store=" + storeId + "#/app";
        } catch (RuntimeException failure) {
            // Never put provider response bodies, codes or credentials into URLs/logs.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Clover connection failed ({})", failure.getClass().getSimpleName());
            destination = "/?clover=failed#/app";
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(destination))
                .header(HttpHeaders.SET_COOKIE, cookie("", request.isSecure(), Duration.ZERO)).build();
    }
    private String cookie(String value, boolean secure, Duration age) {
        return ResponseCookie.from(BINDING_COOKIE, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path(CALLBACK).maxAge(age).build().toString();
    }
}

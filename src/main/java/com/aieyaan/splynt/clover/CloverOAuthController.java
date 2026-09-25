package com.aieyaan.splynt.clover;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/integrations/clover")
public class CloverOAuthController {

    private static final String APP_ID = "4731H0NXZ45WP";

    private static final String REDIRECT_URI =
            "http://localhost:8080/api/integrations/clover/connect";

    private static final String AUTHORIZATION_URL =
            "https://sandbox.dev.clover.com/oauth/v2/authorize";

    @GetMapping("/connect")
    public ResponseEntity<?> connect(
            @RequestParam(
                    name = "merchant_id",
                    required = false
            ) String merchantId,
            @RequestParam(
                    name = "client_id",
                    required = false
            ) String clientId,
            @RequestParam(
                    name = "code",
                    required = false
            ) String authorizationCode) {

        /*
         * Clover returns here again after authorization,
         * this time with a short-lived authorization code.
         */
        if (authorizationCode != null
                && !authorizationCode.isBlank()) {

            return ResponseEntity.ok(
                    "Clover authorization received for merchant "
                            + merchantId
                            + ". You can return to the terminal."
            );
        }

        if (merchantId == null || merchantId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Clover merchant_id is required");
        }

        if (clientId == null || !APP_ID.equals(clientId)) {
            return ResponseEntity.badRequest()
                    .body("Unexpected Clover client_id");
        }

        URI authorizationUri = UriComponentsBuilder
                .fromUriString(AUTHORIZATION_URL)
                .queryParam("client_id", APP_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .build()
                .encode()
                .toUri();

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(authorizationUri)
                .build();
    }
}
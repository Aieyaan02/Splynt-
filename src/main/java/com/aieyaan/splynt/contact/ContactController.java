package com.aieyaan.splynt.contact;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/inquiries")
public class ContactController {
    private final ContactInquiryRepository inquiries;
    public ContactController(ContactInquiryRepository inquiries) { this.inquiries = inquiries; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public synchronized Map<String, String> submit(@Valid @RequestBody InquiryRequest request) {
        // Honeypot submissions do not create records or expose filtering behavior.
        if (request.website() != null && !request.website().isBlank()) return Map.of("status", "received");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (inquiries.countByEmailAndCreatedAtAfter(email, OffsetDateTime.now().minusHours(1)) >= 3
                || inquiries.countByCreatedAtAfter(OffsetDateTime.now().minusMinutes(1)) >= 20)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many inquiries. Please try again later.");
        inquiries.saveAndFlush(new ContactInquiry(request.name(), email, request.business(), request.message()));
        return Map.of("status", "received");
    }
    public record InquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 150) String business,
            @NotBlank @Size(min = 10, max = 3000) String message,
            @Size(max = 255) String website) {}
}

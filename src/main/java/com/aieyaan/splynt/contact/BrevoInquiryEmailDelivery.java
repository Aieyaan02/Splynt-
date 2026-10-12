package com.aieyaan.splynt.contact;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BrevoInquiryEmailDelivery implements InquiryEmailDelivery {
    private final boolean enabled;
    private final String recipient, sender, key;
    private final RestClient client;
    public BrevoInquiryEmailDelivery(
            @Value("${splynt.contact-email.enabled:false}") boolean enabled,
            @Value("${splynt.contact-email.to:}") String recipient,
            @Value("${splynt.contact-email.from:}") String sender,
            @Value("${splynt.contact-email.api-key:}") String key) {
        this.enabled = enabled; this.recipient = recipient; this.sender = sender; this.key = key;
        if (enabled) {
            validateAddress(recipient); validateAddress(sender);
            if (key.isBlank()) throw new IllegalStateException("Contact email API key is missing");
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl("https://api.brevo.com/v3").requestFactory(factory).build();
    }
    private static void validateAddress(String value) {
        try {
            var address = new jakarta.mail.internet.InternetAddress(value, true); address.validate();
            if (!address.getAddress().equals(value) || value.contains("\r") || value.contains("\n")) throw new IllegalArgumentException();
        } catch (Exception invalid) { throw new IllegalStateException("Contact email requires valid sender and recipient addresses"); }
    }
    public boolean configured() { return enabled; }
    public void send(ContactInquiry inquiry) {
        if (!configured()) throw new IllegalStateException("Contact email is disabled");
        // Fixed recipient and subject; visitor input is plain text, never HTML or mail headers.
        var payload = Map.of("sender", Map.of("email", sender, "name", "Splynt"),
                "to", List.of(Map.of("email", recipient)),
                "subject", "Splynt website inquiry #" + inquiry.getId(),
                "textContent", "New website inquiry. Treat message contents as untrusted visitor input.\n\n"
                        + "Name: " + inquiry.getName() + "\nEmail: " + inquiry.getEmail()
                        + "\nBusiness: " + (inquiry.getBusiness() == null ? "Not supplied" : inquiry.getBusiness())
                        + "\nReceived: " + inquiry.getCreatedAt() + "\n\n" + inquiry.getMessage()
                        + "\n\nReply to the visitor's email above, not this automated sender.");
        try {
            client.post().uri("/smtp/email").header("api-key", key)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(payload)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException failure) {
            // Never propagate provider bodies, headers, or personal inquiry content into logs/API errors.
            throw new IllegalStateException("Inquiry notification could not be submitted");
        }
    }
}

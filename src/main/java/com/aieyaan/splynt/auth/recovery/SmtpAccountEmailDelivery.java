package com.aieyaan.splynt.auth.recovery;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpAccountEmailDelivery implements AccountEmailDelivery {
    private final ObjectProvider<JavaMailSender> senders;
    private final boolean enabled;
    private final String from, origin;
    public SmtpAccountEmailDelivery(ObjectProvider<JavaMailSender> senders,
            @Value("${splynt.account-email.enabled:false}") boolean enabled,
            @Value("${splynt.account-email.from:}") String from,
            @Value("${splynt.account-email.origin:}") String origin) {
        this.senders = senders; this.enabled = enabled; this.from = from; this.origin = origin;
        if (enabled) {
            try {
                var uri = URI.create(origin);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException();
                var address = new jakarta.mail.internet.InternetAddress(from, true); address.validate();
                if (!address.getAddress().equals(from) || from.contains("\r") || from.contains("\n")) throw new IllegalArgumentException();
            } catch (Exception invalid) { throw new IllegalStateException("Account email requires a valid sender and trusted HTTPS origin"); }
        }
    }
    public boolean configured() { return enabled && senders.getIfAvailable() != null; }
    public void send(String email, AccountActionToken.Purpose purpose, String token) {
        if (!configured()) throw new IllegalStateException("Account email is unavailable");
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid account link");
        boolean reset = purpose == AccountActionToken.Purpose.RESET_PASSWORD;
        String route = reset ? "reset-password" : "verify-email";
        String link = origin.replaceAll("/$", "") + "/#/" + route + "?token=" + token;
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject(reset ? "Reset your Splynt password" : "Verify your Splynt email");
        message.setText((reset ? "Reset your password using this link (expires in 30 minutes):\n\n" : "Verify your email using this link (expires in 24 hours):\n\n")
                + link + "\n\nUse the most recent email if you requested more than one. If you did not request this, you can ignore it.");
        senders.getObject().send(message);
    }
}

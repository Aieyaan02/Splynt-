package com.aieyaan.splynt.auth.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

class SmtpAccountEmailDeliveryTest {
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider() { return mock(ObjectProvider.class); }
    @Test void disabledDeliveryDoesNotSend() {
        var provider = provider();
        var service = new SmtpAccountEmailDelivery(provider, false, "", "");
        assertFalse(service.configured());
        assertThrows(IllegalStateException.class, () -> service.send("test@example.com", AccountActionToken.Purpose.RESET_PASSWORD, "a".repeat(43)));
        verifyNoInteractions(provider);
    }
    @Test void onlyTrustedHttpsOriginCanCreateLinks() {
        for (String origin : new String[]{"http://example.com", "https://example.com/path", "https://example.com/?x=1", "https://user@example.com", "https://example.com/#redirect"})
            assertThrows(IllegalStateException.class, () -> new SmtpAccountEmailDelivery(provider(), true, "sender@example.com", origin));
    }
    @Test void linkTokenIsInFragmentAndSenderIsExplicit() {
        var provider = provider(); var sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender); when(provider.getObject()).thenReturn(sender);
        var service = new SmtpAccountEmailDelivery(provider, true, "sender@example.com", "https://splynt.example/");
        service.send("customer@example.com", AccountActionToken.Purpose.RESET_PASSWORD, "a".repeat(43));
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(captured.capture());
        assertEquals("sender@example.com", captured.getValue().getFrom());
        assertArrayEquals(new String[]{"customer@example.com"}, captured.getValue().getTo());
        assertTrue(captured.getValue().getText().contains("https://splynt.example/#/reset-password?token=" + "a".repeat(43)));
    }
}

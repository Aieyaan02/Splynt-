package com.aieyaan.splynt.contact;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
class ContactControllerTest {
    ContactInquiryRepository repository = mock(ContactInquiryRepository.class);
    ContactController controller = new ContactController(repository);
    @Test void savesNormalizedContactForFollowUp() {
        controller.submit(new ContactController.InquiryRequest(" Alex ", "ALEX@example.com", "Maple", "I would like a demo", ""));
        var captured = ArgumentCaptor.forClass(ContactInquiry.class);
        verify(repository).saveAndFlush(captured.capture());
        assertEquals("alex@example.com", captured.getValue().getEmail());
        assertEquals("I would like a demo", captured.getValue().getMessage());
    }
    @Test void honeypotDoesNotSave() {
        controller.submit(new ContactController.InquiryRequest("Alex", "alex@example.com", null, "I would like a demo", "spam"));
        verifyNoInteractions(repository);
    }
    @Test void limitsRepeatedRequests() {
        when(repository.countByEmailAndCreatedAtAfter(eq("alex@example.com"), any())).thenReturn(3L);
        assertThrows(ResponseStatusException.class, () -> controller.submit(
                new ContactController.InquiryRequest("Alex", "alex@example.com", null, "I would like a demo", "")));
        verify(repository, never()).saveAndFlush(any());
    }
}

package com.aieyaan.splynt.contact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest @Transactional
class InquiryEmailQueueTest {
    @Autowired ContactInquiryRepository inquiries;
    @Autowired InquiryEmailQueue queue;
    @MockitoBean InquiryEmailDelivery delivery;
    private ContactInquiry inquiry() {
        return inquiries.saveAndFlush(new ContactInquiry("Retailer", "visitor@example.com", "Shop", "Please show me the inventory dashboard."));
    }
    @Test void disabledDeliveryKeepsMessagePendingWithoutSending() {
        var item = inquiry(); queue.deliver(item.getId());
        assertEquals("PENDING", inquiries.findById(item.getId()).orElseThrow().getNotificationState());
        verify(delivery, never()).send(any());
    }
    @Test void acceptedMessageIsNotResent() {
        when(delivery.configured()).thenReturn(true);
        var item = inquiry(); queue.deliver(item.getId()); queue.deliver(item.getId());
        assertEquals("ACCEPTED", item.getNotificationState());
        verify(delivery, times(1)).send(item);
    }
    @Test void failurePreservesInquiryRetriesLaterAndStopsAfterFiveAttempts() {
        when(delivery.configured()).thenReturn(true);
        doThrow(new IllegalStateException("simulated provider failure")).when(delivery).send(any());
        var item = inquiry(); queue.deliver(item.getId());
        assertEquals("PENDING", item.getNotificationState());
        assertTrue(item.getNotificationAvailableAt().isAfter(OffsetDateTime.now()));
        queue.deliver(item.getId()); verify(delivery, times(1)).send(item);
        for (int n = 1; n < 5; n++) {
            ReflectionTestUtils.setField(item, "notificationAvailableAt", OffsetDateTime.now().minusMinutes(1));
            queue.deliver(item.getId());
        }
        assertEquals("FAILED", item.getNotificationState());
        assertEquals(5, item.getNotificationAttempts());
        assertEquals("Please show me the inventory dashboard.", inquiries.findById(item.getId()).orElseThrow().getMessage());
        queue.deliver(item.getId()); verify(delivery, times(5)).send(item);
    }
    @Test void providerCanRecoverOnRetry() {
        when(delivery.configured()).thenReturn(true);
        doThrow(new IllegalStateException("temporary")).doNothing().when(delivery).send(any());
        var item = inquiry(); queue.deliver(item.getId());
        ReflectionTestUtils.setField(item, "notificationAvailableAt", OffsetDateTime.now().minusMinutes(1));
        queue.deliver(item.getId()); assertEquals("ACCEPTED", item.getNotificationState());
    }
    @Test void invalidSenderAndMissingKeyFailBeforeAnyNetworkCall() {
        assertThrows(IllegalStateException.class, () -> new BrevoInquiryEmailDelivery(true, "owner@example.com", "bad\r\naddress", "key"));
        assertThrows(IllegalStateException.class, () -> new BrevoInquiryEmailDelivery(true, "owner@example.com", "sender@example.com", ""));
        assertFalse(new BrevoInquiryEmailDelivery(false, "", "", "").configured());
    }
}

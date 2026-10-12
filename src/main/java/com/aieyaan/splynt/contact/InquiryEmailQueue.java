package com.aieyaan.splynt.contact;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InquiryEmailQueue {
    private final ContactInquiryRepository inquiries;
    private final InquiryEmailDelivery delivery;
    public InquiryEmailQueue(ContactInquiryRepository inquiries, InquiryEmailDelivery delivery) {
        this.inquiries = inquiries; this.delivery = delivery;
    }
    @Transactional
    public void deliver(Long id) {
        if (!delivery.configured()) return;
        var inquiry = inquiries.findLockedById(id).orElse(null);
        var now = OffsetDateTime.now();
        if (inquiry == null || !inquiry.notificationReady(now)) return;
        try { delivery.send(inquiry); inquiry.notificationAccepted(); }
        catch (RuntimeException failure) {
            inquiry.notificationFailed(now);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Inquiry notification attempt failed for inquiry {}", id);
        }
    }
}

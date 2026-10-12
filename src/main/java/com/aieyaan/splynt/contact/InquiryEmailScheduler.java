package com.aieyaan.splynt.contact;

import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(name = "splynt.contact-email.enabled", havingValue = "true")
public class InquiryEmailScheduler {
    private final ContactInquiryRepository inquiries;
    private final InquiryEmailQueue queue;
    public InquiryEmailScheduler(ContactInquiryRepository inquiries, InquiryEmailQueue queue) {
        this.inquiries = inquiries; this.queue = queue;
    }
    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public void deliverPending() {
        for (var item : inquiries.findTop10ByNotificationStateAndNotificationAvailableAtLessThanEqualOrderById("PENDING", OffsetDateTime.now())) {
            try { queue.deliver(item.getId()); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Inquiry notification processing failed for inquiry {}", item.getId());
            }
        }
    }
}

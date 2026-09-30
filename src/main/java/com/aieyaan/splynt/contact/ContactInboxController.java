package com.aieyaan.splynt.contact;
import java.time.OffsetDateTime;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/operations/inquiries")
@PreAuthorize("@contactAccess.allowed(authentication)")
public class ContactInboxController {
    private final ContactInquiryRepository inquiries;
    public ContactInboxController(ContactInquiryRepository inquiries) { this.inquiries = inquiries; }
    @GetMapping
    public Page<InquiryView> list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid inbox page");
        return inquiries.findAll(PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(i -> new InquiryView(i.getId(), i.getName(), i.getEmail(), i.getBusiness(), i.getMessage(), i.getCreatedAt()));
    }
    public record InquiryView(Long id, String name, String email, String business, String message, OffsetDateTime createdAt) {}
}

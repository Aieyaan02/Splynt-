package com.aieyaan.splynt.contact;

public interface InquiryEmailDelivery {
    boolean configured();
    void send(ContactInquiry inquiry);
}

package com.aieyaan.splynt.auth.recovery;

public interface AccountEmailDelivery {
    boolean configured();
    void send(String email, AccountActionToken.Purpose purpose, String token);
}

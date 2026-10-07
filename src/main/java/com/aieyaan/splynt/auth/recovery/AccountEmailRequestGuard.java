package com.aieyaan.splynt.auth.recovery;
import jakarta.persistence.*;
@Entity @Table(name = "account_email_request_guard")
public class AccountEmailRequestGuard {
    @Id private Integer id;
    protected AccountEmailRequestGuard() { }
    public AccountEmailRequestGuard(Integer id) { this.id = id; }
}

package com.aieyaan.splynt.auth;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import com.aieyaan.splynt.auth.dto.LoginRequest;
import com.aieyaan.splynt.auth.dto.RegisterRequest;

class PasswordValidationTest {
    @Test void registrationAndLoginRejectOverlongUtf8BeforeEncoding() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String valid : new String[]{"a".repeat(72), "é".repeat(36), "🔑".repeat(18)}) {
                assertTrue(validator.validate(registration(valid)).isEmpty());
                assertTrue(validator.validate(new LoginRequest("test@example.com", valid)).isEmpty());
            }
            for (String invalid : new String[]{"a".repeat(73), "é".repeat(37), "🔑".repeat(19)}) {
                assertTrue(validator.validate(registration(invalid)).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
                assertTrue(validator.validate(new LoginRequest("test@example.com", invalid)).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
            }
        }
    }
    private RegisterRequest registration(String password) {
        return new RegisterRequest("test@example.com", password, "Test", "User", "Company", "company", "Store", "store");
    }
}

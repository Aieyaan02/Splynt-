package com.aieyaan.splynt.auth.validation;

import java.nio.charset.StandardCharsets;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordByteLimitValidator implements ConstraintValidator<PasswordByteLimit, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}

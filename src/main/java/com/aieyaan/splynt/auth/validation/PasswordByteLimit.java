package com.aieyaan.splynt.auth.validation;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLimitValidator.class)
public @interface PasswordByteLimit {
    String message() default "Password is too long when encoded. Use at most 72 UTF-8 bytes; emoji and some letters use more than one byte.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

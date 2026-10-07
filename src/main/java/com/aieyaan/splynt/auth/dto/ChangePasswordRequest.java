package com.aieyaan.splynt.auth.dto;

import jakarta.validation.constraints.*;
import com.aieyaan.splynt.auth.validation.PasswordByteLimit;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) @PasswordByteLimit String currentPassword,
        @NotBlank @Size(min = 10, max = 72, message = "Use at least 10 characters for your new password")
        @PasswordByteLimit String newPassword) { }

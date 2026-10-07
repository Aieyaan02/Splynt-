package com.aieyaan.splynt.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                min = 10,
                max = 72,
                message = "Password must contain between 10 and 72 characters"
        )
        @com.aieyaan.splynt.auth.validation.PasswordByteLimit
        String password,

        @NotBlank(message = "First name is required")
        @Size(
                max = 80,
                message = "First name cannot exceed 80 characters"
        )
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(
                max = 80,
                message = "Last name cannot exceed 80 characters"
        )
        String lastName,

        @NotBlank(message = "Organization name is required")
        @Size(
                max = 150,
                message = "Organization name cannot exceed 150 characters"
        )
        String organizationName,

        @NotBlank(message = "Organization slug is required")
        @Size(
                min = 3,
                max = 100,
                message = "Organization slug must contain between 3 and 100 characters"
        )
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Organization slug may contain lowercase letters, numbers, and single hyphens"
        )
        String organizationSlug,

        @NotBlank(message = "Store name is required")
        @Size(
                max = 150,
                message = "Store name cannot exceed 150 characters"
        )
        String storeName,

        @NotBlank(message = "Store slug is required")
        @Size(
                min = 3,
                max = 100,
                message = "Store slug must contain between 3 and 100 characters"
        )
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Store slug may contain lowercase letters, numbers, and single hyphens"
        )
        String storeSlug
) {
}
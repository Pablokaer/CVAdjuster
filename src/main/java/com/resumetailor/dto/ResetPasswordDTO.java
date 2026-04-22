package com.resumetailor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    // Carried as a hidden field in the reset-password form
    @NotBlank
    private String token;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotBlank(message = "Please confirm your password.")
    private String confirmPassword;
}

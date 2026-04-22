package com.resumetailor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordDTO {

    @Email(message = "Please enter a valid email address.")
    @NotBlank(message = "Email is required.")
    private String email;
}

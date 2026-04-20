package com.resumetailor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {

  @Email(message = "Email inválido")
  @NotBlank(message = "Email obrigatório")
  private String email;

  @NotBlank(message = "Senha obrigatória")
  private String password;
}
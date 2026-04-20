package com.resumetailor.controller;

import com.resumetailor.dto.RegisterDTO;
import com.resumetailor.model.User;
import com.resumetailor.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;

  @GetMapping("/login")
  public String login() {
    return "login";
  }

  @GetMapping("/register")
  public String registerPage(Model model) {
    model.addAttribute("user", new RegisterDTO());
    return "register";
  }

  @PostMapping("/register")
  public String register(
      @ModelAttribute("user") @Valid RegisterDTO dto,
      HttpServletRequest request
  ) {

    if (repository.findByEmail(dto.getEmail()).isPresent()) {
      return "redirect:/register?error";
    }

    User user = new User();
    user.setEmail(dto.getEmail());
    user.setPassword(passwordEncoder.encode(dto.getPassword()));
    user.setProvider("LOCAL");

    repository.save(user);

    try {
      request.login(dto.getEmail(), dto.getPassword());
    } catch (ServletException e) {
      log.error("Auto-login failed after registration for {}", dto.getEmail(), e);
    }

    return "redirect:/";
  }
}
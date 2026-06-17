package com.resumetailor.controller;

import com.resumetailor.dto.RegisterDTO;
import com.resumetailor.exception.EmailAlreadyRegisteredException;
import com.resumetailor.model.User;
import com.resumetailor.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
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
            Model model,
            HttpServletRequest request
    ) {
        User saved;
        try {
            saved = userService.register(dto);
            // UserService publishes UserRegisteredEvent inside its @Transactional boundary.
            // NotificationEventListener fires the welcome email after commit — nothing to do here.
        } catch (EmailAlreadyRegisteredException e) {
            model.addAttribute("error", "This email is already registered. Please log in.");
            return "register";
        }

        try {
            request.login(dto.getEmail(), dto.getPassword());
        } catch (ServletException e) {
            log.error("Auto-login failed after registration for {}", dto.getEmail(), e);
        }

        return "redirect:/";
    }
}

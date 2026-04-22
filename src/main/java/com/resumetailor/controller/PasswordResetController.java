package com.resumetailor.controller;

import com.resumetailor.dto.ForgotPasswordDTO;
import com.resumetailor.dto.ResetPasswordDTO;
import com.resumetailor.exception.InvalidTokenException;
import com.resumetailor.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ── forgot-password ───────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(
            @RequestParam(required = false) String sent,
            @RequestParam(required = false) String invalid,
            Model model
    ) {
        boolean isSent    = sent    != null;
        boolean isInvalid = invalid != null;
        model.addAttribute("sent",    isSent);
        model.addAttribute("invalid", isInvalid);
        if (!isSent && !isInvalid) {
            model.addAttribute("dto", new ForgotPasswordDTO());
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(
            @ModelAttribute("dto") @Valid ForgotPasswordDTO dto,
            BindingResult result,
            Model model
    ) {
        if (result.hasErrors()) {
            model.addAttribute("sent",    false);
            model.addAttribute("invalid", false);
            return "forgot-password";
        }
        // Service never throws — even unknown emails complete silently
        passwordResetService.requestReset(dto.getEmail());

        // Always redirect to the same confirmation page regardless of outcome
        return "redirect:/forgot-password?sent";
    }

    // ── reset-password ────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        try {
            passwordResetService.validateToken(token);
        } catch (InvalidTokenException e) {
            // Token invalid or expired — send back to start with a generic message
            return "redirect:/forgot-password?invalid";
        }
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setToken(token);
        model.addAttribute("dto", dto);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @ModelAttribute("dto") @Valid ResetPasswordDTO dto,
            BindingResult result,
            Model model
    ) {
        if (result.hasErrors()) {
            String firstError = result.getAllErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("Please correct the errors below.");
            model.addAttribute("error", firstError);
            return "reset-password";
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }
        try {
            passwordResetService.resetPassword(dto.getToken(), dto.getPassword());
            return "redirect:/reset-password-success";
        } catch (InvalidTokenException e) {
            return "redirect:/forgot-password?invalid";
        } catch (Exception e) {
            log.error("Unexpected error during password reset", e);
            model.addAttribute("error", "An unexpected error occurred. Please try again.");
            return "reset-password";
        }
    }

    @GetMapping("/reset-password-success")
    public String resetPasswordSuccess() {
        return "reset-password-success";
    }
}

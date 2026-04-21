package com.resumetailor.controller;

import com.resumetailor.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final UserService userService;

    @ModelAttribute("userCredits")
    public Integer userCredits(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String email = UserService.extractEmail(authentication);
        if (email == null) return 0;
        return userService.getCredits(email);
    }
}

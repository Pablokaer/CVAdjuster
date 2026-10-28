package com.resumetailor.controller;

import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    /** Credit plans for the pricing section, which several views render. */
    @ModelAttribute("plans")
    public CreditPlan[] plans() {
        return CreditPlan.values();
    }

    /**
     * Google profile picture for OAuth2 users, null for form-login users.
     * Resolved here because Thymeleaf 3.1.5+ forbids T(...) type references to
     * non-JDK classes in templates, so the check cannot live in the view.
     */
    @ModelAttribute("userPicture")
    public String userPicture(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2) {
            Object picture = oauth2.getAttributes().get("picture");
            return picture instanceof String url && !url.isBlank() ? url : null;
        }
        return null;
    }
}

package com.resumetailor.controller;

import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.payment.service.PaymentService;
import com.resumetailor.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CreditsController {

    private final PaymentService paymentService;
    private final UserService userService;

    @GetMapping("/credits")
    public String creditsPage(
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Boolean canceled,
            @RequestParam(required = false, name = "session_id") String sessionId,
            Authentication authentication,
            Model model) {

        model.addAttribute("plans", CreditPlan.values());
        model.addAttribute("creditsPerResume", CreditPlan.CREDITS_PER_RESUME);

        if (Boolean.TRUE.equals(success) && sessionId != null) {
            boolean credited = paymentService.reconcileSuccessfulSession(sessionId);
            model.addAttribute("toast", credited ? "success" : "pending");
            log.info("Success redirect for session {} — credited={}", sessionId, credited);
        } else if (Boolean.TRUE.equals(canceled)) {
            model.addAttribute("toast", "canceled");
        }

        // Refresh credit balance after any potential reconciliation so the template
        // shows the up-to-date value (GlobalModelAttributes runs before this method).
        if (authentication != null && authentication.isAuthenticated()) {
            String email = UserService.extractEmail(authentication);
            if (email != null) {
                model.addAttribute("userCredits", userService.getCredits(email));
            }
        }

        return "credits";
    }
}

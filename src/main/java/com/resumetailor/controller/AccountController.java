package com.resumetailor.controller;

import com.resumetailor.model.User;
import com.resumetailor.service.AccountService;
import com.resumetailor.service.AccountService.DeletionResult;
import com.resumetailor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Account page and account deletion, plus the public pages Google Play links to. */
@Controller
@RequiredArgsConstructor
public class AccountController {

    static final String CONFIRMATION_WORD = "DELETE";

    private final AccountService accountService;

    @GetMapping("/account")
    public String account(Model model, Authentication authentication) {
        String email = UserService.extractEmail(authentication);
        User user = accountService.findByEmail(email).orElse(null);
        model.addAttribute("email", email);
        model.addAttribute("hasPassword", user != null && user.getPassword() != null);
        return "account";
    }

    @PostMapping("/account/delete")
    public String deleteAccount(@RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmation,
                                Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model) {
        String email = UserService.extractEmail(authentication);

        if (!CONFIRMATION_WORD.equals(confirmation == null ? null : confirmation.trim())) {
            return accountWithError(model, authentication, "Type DELETE to confirm.");
        }

        DeletionResult result = accountService.deleteAccount(email, password);
        if (result == DeletionResult.WRONG_PASSWORD) {
            return accountWithError(model, authentication, "That password is not correct.");
        }

        // DELETED, or NOT_FOUND (already gone): either way the session must end.
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/?accountDeleted";
    }

    /** Public page explaining how to delete an account; this is the URL given to Google Play. */
    @GetMapping("/delete-account")
    public String deleteAccountInfo() {
        return "delete-account";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    private String accountWithError(Model model, Authentication authentication, String error) {
        model.addAttribute("deleteError", error);
        return account(model, authentication);
    }
}

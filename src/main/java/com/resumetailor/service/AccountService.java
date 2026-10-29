package com.resumetailor.service;

import com.resumetailor.model.ResumeHistory;
import com.resumetailor.model.User;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Account deletion, as required by Google Play for apps that let users create accounts.
 *
 * Deleting the user row removes the resume history and password reset tokens through
 * ON DELETE CASCADE. Orders are kept for accounting: they hold only the amount, the
 * plan, the Stripe session ID and the numeric user ID, which no longer points anywhere.
 * A payment that completes after the deletion is ignored by PaymentService.fulfil,
 * because there is no user left to credit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    public enum DeletionResult { DELETED, WRONG_PASSWORD, NOT_FOUND }

    private final UserRepository userRepository;
    private final ResumeHistoryRepository historyRepository;
    private final ResumeTailorService resumeTailorService;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Deletes the account and everything tied to it. Accounts with a password must
     * confirm it; accounts without one (Google sign-in) rely on the active session.
     */
    public DeletionResult deleteAccount(String email, String password) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) return DeletionResult.NOT_FOUND;

        User user = found.get();
        if (user.getPassword() != null
                && (password == null || !passwordEncoder.matches(password, user.getPassword()))) {
            log.warn("SECURITY: account deletion rejected, wrong password, userId={}", user.getId());
            return DeletionResult.WRONG_PASSWORD;
        }

        List<String> generatedFiles = historyRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(h -> h.getPdfFilename() != null ? h.getPdfFilename() : h.getDocxFilename())
                .toList();

        userRepository.delete(user);

        // Best effort: the hourly cleanup removes anything left within 24 hours anyway.
        generatedFiles.forEach(resumeTailorService::deleteGeneratedFiles);

        log.info("Account deleted, userId={}", user.getId());
        return DeletionResult.DELETED;
    }
}

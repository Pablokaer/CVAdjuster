package com.resumetailor.service;

import com.resumetailor.model.ResumeHistory;
import com.resumetailor.model.User;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.AccountService.DeletionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;
    @Mock private ResumeHistoryRepository historyRepository;
    @Mock private ResumeTailorService resumeTailorService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AccountService accountService;

    @Test
    void wrongPasswordDeletesNothing() {
        User user = user("hash");
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThat(accountService.deleteAccount(EMAIL, "wrong")).isEqualTo(DeletionResult.WRONG_PASSWORD);
        assertThat(accountService.deleteAccount(EMAIL, null)).isEqualTo(DeletionResult.WRONG_PASSWORD);

        verify(userRepository, never()).delete(any());
        verify(resumeTailorService, never()).deleteGeneratedFiles(anyString());
    }

    @Test
    void correctPasswordDeletesTheUserAndTheirFiles() {
        User user = user("hash");
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        ResumeHistory entry = new ResumeHistory();
        entry.setPdfFilename("resume-tailored-abc.pdf");
        when(historyRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(entry));

        assertThat(accountService.deleteAccount(EMAIL, "secret")).isEqualTo(DeletionResult.DELETED);

        verify(userRepository).delete(user);
        verify(resumeTailorService).deleteGeneratedFiles("resume-tailored-abc.pdf");
    }

    @Test
    void accountWithoutPasswordIsDeletedFromItsSession() {
        User user = user(null);
        when(historyRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        assertThat(accountService.deleteAccount(EMAIL, null)).isEqualTo(DeletionResult.DELETED);
        verify(userRepository).delete(user);
    }

    @Test
    void unknownAccountIsReported() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(accountService.deleteAccount(EMAIL, "secret")).isEqualTo(DeletionResult.NOT_FOUND);
        verify(userRepository, never()).delete(any());
    }

    private User user(String passwordHash) {
        User user = new User();
        user.setId(7L);
        user.setEmail(EMAIL);
        user.setPassword(passwordHash);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }
}

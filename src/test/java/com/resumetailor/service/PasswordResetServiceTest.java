package com.resumetailor.service;

import com.resumetailor.model.PasswordResetToken;
import com.resumetailor.model.User;
import com.resumetailor.repository.PasswordResetTokenRepository;
import com.resumetailor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;

    @InjectMocks private PasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setEmail(EMAIL);
    }

    @Test
    void newRequestRemovesOnlyDeadTokensAndKeepsEarlierLinks() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        verify(tokenRepository).deleteDeadTokensForUser(eq(user), any(Instant.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(notificationService).sendPasswordResetEmail(eq(user), anyString());
    }

    @Test
    void requestWithinCooldownSavesNothingAndSendsNoEmail() {
        PasswordResetToken recent = new PasswordResetToken();
        recent.setUser(user);
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        recent.setExpiresAt(Instant.now().plusSeconds(3600));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user)).thenReturn(Optional.of(recent));

        service.requestReset(EMAIL);

        verify(tokenRepository, never()).save(any());
        verify(tokenRepository, never()).deleteDeadTokensForUser(any(), any());
        verify(notificationService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void successfulResetRevokesAllTokensForUser() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded");

        service.resetPassword("raw-token", "new-password");

        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUser(user);
    }
}

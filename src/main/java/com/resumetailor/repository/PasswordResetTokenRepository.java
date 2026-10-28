package com.resumetailor.repository;

import com.resumetailor.model.PasswordResetToken;
import com.resumetailor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.user WHERE t.token = :token")
    Optional<PasswordResetToken> findByToken(@Param("token") String token);

    // A user may hold several live tokens; the most recent one drives the cooldown
    Optional<PasswordResetToken> findFirstByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    // Removes only tokens that can no longer be used, so earlier links stay valid
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = :user AND (t.used = true OR t.expiresAt < :now)")
    void deleteDeadTokensForUser(@Param("user") User user, @Param("now") Instant now);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant threshold);
}

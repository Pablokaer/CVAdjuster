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

    // Used to check per-user cooldown before issuing a new token
    Optional<PasswordResetToken> findByUserAndUsedFalse(User user);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant threshold);
}

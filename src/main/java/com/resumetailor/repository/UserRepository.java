package com.resumetailor.repository;

import com.resumetailor.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  // Credit changes run as single UPDATE statements so concurrent requests can
  // neither lose an update nor spend the same credit twice.

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE User u SET u.credits = u.credits + :amount WHERE u.id = :userId")
  int incrementCredits(@Param("userId") Long userId, @Param("amount") int amount);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE User u SET u.credits = u.credits + :amount WHERE u.email = :email")
  int incrementCreditsByEmail(@Param("email") String email, @Param("amount") int amount);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE User u SET u.credits = u.credits - :amount WHERE u.email = :email AND u.credits >= :amount")
  int decrementCreditsIfSufficient(@Param("email") String email, @Param("amount") int amount);
}

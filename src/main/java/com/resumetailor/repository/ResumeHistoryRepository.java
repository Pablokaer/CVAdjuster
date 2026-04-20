package com.resumetailor.repository;

import com.resumetailor.model.ResumeHistory;
import com.resumetailor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeHistoryRepository extends JpaRepository<ResumeHistory, Long> {
    List<ResumeHistory> findByUserOrderByCreatedAtDesc(User user);
}

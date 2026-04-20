package com.resumetailor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "resume_history")
public class ResumeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 200)
    private String jobDescriptionSnippet;

    @Column(columnDefinition = "TEXT")
    private String tailoredText;

    @Column(length = 10)
    private String format;

    private String pdfFilename;
    private String docxFilename;
}

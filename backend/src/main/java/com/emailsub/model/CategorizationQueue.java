package com.emailsub.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "categorization_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorizationQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String domain;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sample_subjects", columnDefinition = "TEXT")
    private String sampleSubjects; // JSON array stored as text

    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "status")
    private String status = "pending"; // pending, processing, done, failed

    @Column(name = "priority")
    private int priority = 5;

    @Column(name = "attempts")
    private int attempts = 0;

    @Column(name = "max_attempts")
    private int maxAttempts = 3;

    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "assigned_category")
    private String assignedCategory;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

package com.emailsub.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "community_senders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunitySender {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String domain;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(nullable = false)
    private String category;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "verified_count")
    private int verifiedCount = 0;

    @Column(name = "correction_count")
    private int correctionCount = 0;

    @Column(name = "categorized_by")
    private String categorizedBy;

    @Column(name = "sample_subjects", columnDefinition = "TEXT[]")
    private String[] sampleSubjects;

    @Column(name = "is_trusted")
    private boolean isTrusted = false;

    @Column(name = "is_spam")
    private boolean isSpam = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.emailsub.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    // Gmail fields
    @Column(name = "gmail_connected")
    private boolean gmailConnected = false;

    @Column(name = "gmail_access_token", columnDefinition = "TEXT")
    private String gmailAccessToken;

    @Column(name = "gmail_refresh_token", columnDefinition = "TEXT")
    private String gmailRefreshToken;

    @Column(name = "gmail_token_expiry")
    private LocalDateTime gmailTokenExpiry;

    @Column(name = "gmail_sync_token", columnDefinition = "TEXT")
    private String gmailSyncToken;

    @Column(name = "gmail_last_sync")
    private LocalDateTime gmailLastSync;

    // Outlook fields
    @Column(name = "outlook_connected")
    private boolean outlookConnected = false;

    @Column(name = "outlook_access_token", columnDefinition = "TEXT")
    private String outlookAccessToken;

    @Column(name = "outlook_refresh_token", columnDefinition = "TEXT")
    private String outlookRefreshToken;

    @Column(name = "outlook_token_expiry")
    private LocalDateTime outlookTokenExpiry;

    @Column(name = "outlook_delta_token", columnDefinition = "TEXT")
    private String outlookDeltaToken;

    @Column(name = "outlook_last_sync")
    private LocalDateTime outlookLastSync;

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

package com.emailsub.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "sender_email", "account_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_sender_id")
    private CommunitySender communitySender;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "total_email_count")
    private int totalEmailCount = 0;

    @Column(name = "email_count_7days")
    private int emailCount7days = 0;

    @Column(name = "email_count_30days")
    private int emailCount30days = 0;

    @Column(name = "last_email_at")
    private LocalDateTime lastEmailAt;

    @Column(name = "first_email_at")
    private LocalDateTime firstEmailAt;

    @Column(name = "unsubscribe_link", columnDefinition = "TEXT")
    private String unsubscribeLink;

    @Column(name = "unsubscribe_mailto")
    private String unsubscribeMailto;

    @Column(name = "unsubscribe_type")
    private String unsubscribeType; // one-click, mailto, manual

    @Column(name = "status")
    private String status = "active"; // active, unsubscribed, pending

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    @Column(name = "account_type", nullable = false)
    private String accountType; // gmail, outlook

    @Column(name = "custom_category")
    private String customCategory;

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

    public String getEffectiveCategory() {
        if (customCategory != null && !customCategory.isEmpty()) return customCategory;
        if (communitySender != null) return communitySender.getCategory();
        return "Other";
    }
}

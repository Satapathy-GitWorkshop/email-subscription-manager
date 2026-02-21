package com.emailsub.repository;

import com.emailsub.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    List<UserSubscription> findByUserId(UUID userId);

    List<UserSubscription> findByUserIdAndAccountType(UUID userId, String accountType);

    List<UserSubscription> findByUserIdAndStatus(UUID userId, String status);

    Optional<UserSubscription> findByUserIdAndSenderEmailAndAccountType(
            UUID userId, String senderEmail, String accountType);

    boolean existsByUserIdAndSenderEmailAndAccountType(
            UUID userId, String senderEmail, String accountType);

    @Query("SELECT us FROM UserSubscription us JOIN FETCH us.communitySender cs " +
           "WHERE us.user.id = :userId ORDER BY us.emailCount30days DESC")
    List<UserSubscription> findByUserIdWithSender(@Param("userId") UUID userId);

    @Query("SELECT us FROM UserSubscription us WHERE us.user.id = :userId " +
           "AND (us.customCategory = :category OR " +
           "(us.customCategory IS NULL AND us.communitySender.category = :category))")
    List<UserSubscription> findByUserIdAndCategory(
            @Param("userId") UUID userId, @Param("category") String category);

    long countByUserIdAndStatus(UUID userId, String status);
}

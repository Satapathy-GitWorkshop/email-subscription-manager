package com.emailsub.repository;

import com.emailsub.model.UserCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserCorrectionRepository extends JpaRepository<UserCorrection, UUID> {

    @Query("SELECT uc.correctedCategory, COUNT(uc) as cnt FROM UserCorrection uc " +
           "WHERE uc.communitySender.id = :senderId " +
           "GROUP BY uc.correctedCategory ORDER BY cnt DESC")
    List<Object[]> findTopCorrectionForSender(@Param("senderId") UUID senderId);

    long countByCommunitySenderId(UUID senderId);
}

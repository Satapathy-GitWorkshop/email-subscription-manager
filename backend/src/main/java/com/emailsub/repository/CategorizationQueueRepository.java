package com.emailsub.repository;

import com.emailsub.model.CategorizationQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategorizationQueueRepository extends JpaRepository<CategorizationQueue, UUID> {

    @Query("SELECT cq FROM CategorizationQueue cq WHERE cq.status = 'pending' " +
           "AND cq.attempts < cq.maxAttempts ORDER BY cq.priority ASC, cq.createdAt ASC")
    List<CategorizationQueue> findPendingItems();

    Optional<CategorizationQueue> findByDomainAndStatus(String domain, String status);

    boolean existsByDomainAndStatusIn(String domain, List<String> statuses);
}

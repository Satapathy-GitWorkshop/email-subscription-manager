package com.emailsub.repository;

import com.emailsub.model.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {
    List<SyncLog> findByUserIdOrderByStartedAtDesc(UUID userId);
}

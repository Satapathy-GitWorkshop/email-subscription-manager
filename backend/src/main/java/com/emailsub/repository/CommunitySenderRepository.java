package com.emailsub.repository;

import com.emailsub.model.CommunitySender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunitySenderRepository extends JpaRepository<CommunitySender, UUID> {
    Optional<CommunitySender> findByDomain(String domain);
    boolean existsByDomain(String domain);

    @Query("SELECT cs FROM CommunitySender cs WHERE cs.domain = :domain AND cs.isTrusted = true")
    Optional<CommunitySender> findTrustedByDomain(String domain);
}

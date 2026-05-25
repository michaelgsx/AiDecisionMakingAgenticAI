package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.QaFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QaFeedbackRepository extends JpaRepository<QaFeedback, UUID> {

    Optional<QaFeedback> findByMessageId(UUID messageId);
}

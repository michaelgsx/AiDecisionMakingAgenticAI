package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.QaMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QaMessageRepository extends JpaRepository<QaMessage, UUID> {

    List<QaMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}

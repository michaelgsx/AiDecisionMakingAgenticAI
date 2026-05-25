package com.aidecision.agentic.repository;

import com.aidecision.agentic.entity.QaConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QaConversationRepository extends JpaRepository<QaConversation, UUID> {
}

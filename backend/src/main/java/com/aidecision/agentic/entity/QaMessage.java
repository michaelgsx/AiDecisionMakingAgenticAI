package com.aidecision.agentic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qa_message")
public class QaMessage {

    @Id
    @Column(name = "message_id", columnDefinition = "uniqueidentifier")
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID conversationId;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (messageId == null) {
            messageId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Instant getCreatedAt() { return createdAt; }
}

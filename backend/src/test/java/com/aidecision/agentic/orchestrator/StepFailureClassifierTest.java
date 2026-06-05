package com.aidecision.agentic.orchestrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepFailureClassifierTest {

    private final StepFailureClassifier classifier = new StepFailureClassifier();

    @Test
    void classify_contextTooLarge() {
        assertThat(classifier.classify("context_length_exceeded: reduce input"))
                .isEqualTo(StepFailureKind.CONTEXT_TOO_LARGE);
    }

    @Test
    void classify_databaseConnection() {
        assertThat(classifier.classify("TCP/IP connection to host timed out"))
                .isEqualTo(StepFailureKind.DATABASE_CONNECTION);
    }

    @Test
    void classify_databasePermission() {
        assertThat(classifier.classify("No schema tables permitted for user: analyst"))
                .isEqualTo(StepFailureKind.DATABASE_PERMISSION);
    }

    @Test
    void classify_unknownIsRetryable() {
        assertThat(classifier.classify("upstream service returned 503"))
                .isEqualTo(StepFailureKind.RETRYABLE);
    }

    @Test
    void userFacingMessage_databaseConnectionIsExplicit() {
        String msg = classifier.userFacingMessage(StepFailureKind.DATABASE_CONNECTION, "raw");
        assertThat(msg).containsIgnoringCase("connection");
    }
}

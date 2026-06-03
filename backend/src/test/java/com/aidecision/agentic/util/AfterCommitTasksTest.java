package com.aidecision.agentic.util;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTasksTest {

    @Test
    void runWithoutActiveTransactionExecutesImmediately() {
        AfterCommitTasks tasks = new AfterCommitTasks();
        AtomicBoolean ran = new AtomicBoolean(false);
        tasks.run(() -> ran.set(true));
        assertThat(ran).isTrue();
    }

    @Test
    void runWithActiveTransactionDefersUntilCommit() {
        AfterCommitTasks tasks = new AfterCommitTasks();
        AtomicBoolean ran = new AtomicBoolean(false);

        TransactionSynchronizationManager.initSynchronization();
        try {
            tasks.run(() -> ran.set(true));
            assertThat(ran).isFalse();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(ran).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

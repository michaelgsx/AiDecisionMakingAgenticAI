package com.aidecision.agentic.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlySqlValidatorTest {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();

    @Test
    void validSelect_passes() {
        assertThatCode(() -> validator.validate("SELECT TOP 10 * FROM dbo.risk_features"))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteKeyword_fails() {
        assertThatThrownBy(() -> validator.validate("SELECT * FROM t; DELETE FROM t"))
                .hasMessageContaining("semicolon");
    }

    @Test
    void nonSelect_fails() {
        assertThatThrownBy(() -> validator.validate("UPDATE dbo.risk_features SET scenario = 'x'"))
                .hasMessageContaining("SELECT");
    }

    @Test
    void forbiddenKeyword_fails() {
        assertThatThrownBy(() -> validator.validate("SELECT * INTO #tmp FROM dbo.risk_features DROP TABLE #tmp"))
                .hasMessageContaining("forbidden");
    }
}

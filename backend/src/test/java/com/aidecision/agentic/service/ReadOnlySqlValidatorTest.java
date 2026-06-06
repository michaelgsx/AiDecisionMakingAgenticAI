package com.aidecision.agentic.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void validateAndNormalize_fixesTopBeforeDistinct() {
        String sql = validator.validateAndNormalize(
                "SELECT TOP 100 DISTINCT rf.user_id FROM dbo.risk_features rf WHERE rf.user_id IS NOT NULL");
        assertThat(sql).startsWith("SELECT DISTINCT TOP 100");
    }

    @Test
    void countDistinctOver_fails() {
        assertThatThrownBy(() -> validator.validate("""
                SELECT TOP 100 COUNT(DISTINCT rf.user_id) OVER () AS total, rf.user_id
                FROM dbo.risk_features rf GROUP BY rf.user_id
                """))
                .hasMessageContaining("COUNT(DISTINCT");
    }
}

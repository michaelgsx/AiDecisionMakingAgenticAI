package com.aidecision.agentic.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlServerSyntaxRulesTest {

    @Test
    void normalize_fixesTopBeforeDistinct() {
        String fixed = SqlServerSyntaxRules.normalize(
                "SELECT TOP 100 DISTINCT rf.user_id FROM dbo.risk_features rf");
        assertThat(fixed).isEqualToIgnoringCase(
                "SELECT DISTINCT TOP 100 rf.user_id FROM dbo.risk_features rf");
    }

    @Test
    void validate_rejectsTopBeforeDistinctWhenNotNormalized() {
        assertThatThrownBy(() -> SqlServerSyntaxRules.validate(
                "SELECT TOP 100 DISTINCT rf.user_id FROM dbo.risk_features rf"))
                .hasMessageContaining("DISTINCT TOP");
    }

    @Test
    void validate_acceptsDistinctTopOrder() {
        assertThatCode(() -> SqlServerSyntaxRules.validate(
                "SELECT DISTINCT TOP 100 rf.user_id FROM dbo.risk_features rf"))
                .doesNotThrowAnyException();
    }
}

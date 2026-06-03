package com.aidecision.agentic.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void questionLogsLengthOnly() {
        assertThat(LogSanitizer.question("show me user id 'songxiang1'"))
                .isEqualTo("[question len=28]");
    }

    @Test
    void userIdIsMasked() {
        assertThat(LogSanitizer.userId("songxiang1")).isEqualTo("s***1");
    }

    @Test
    void textRedactsEmailAndQuotedValues() {
        String sanitized = LogSanitizer.text("Contact songxiang.gu@gmail.com about 'songxiang1'");
        assertThat(sanitized).contains("[email]");
        assertThat(sanitized).contains("'[redacted]'");
        assertThat(sanitized).doesNotContain("songxiang");
    }

    @Test
    void jsonSummaryRedactsSensitiveFields() {
        String summary = LogSanitizer.jsonSummary("""
                {"rowCount":2,"sql":"SELECT * FROM users WHERE id='x'","rows":[{"user_id":"u1"}]}
                """);
        assertThat(summary).contains("rowCount=2");
        assertThat(summary).contains("sql=[redacted]");
        assertThat(summary).contains("rows=[redacted]");
        assertThat(summary).doesNotContain("SELECT");
    }
}

package com.aidecision.agentic.controller;

import com.aidecision.agentic.config.QaUiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaConfigControllerTest {

    @Test
    void sampleQuestions_trimsAndDropsBlank() {
        QaUiProperties props = new QaUiProperties();
        props.setSampleQuestions(List.of(
                " show me user-001 ",
                "",
                "how many cases?"));

        var response = new QaConfigController(props).sampleQuestions();

        assertThat(response.questions()).containsExactly(
                "show me user-001",
                "how many cases?");
    }
}

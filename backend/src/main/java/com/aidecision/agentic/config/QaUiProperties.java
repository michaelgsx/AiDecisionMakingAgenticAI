package com.aidecision.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.qa")
public class QaUiProperties {

    private List<String> sampleQuestions = new ArrayList<>();

    public List<String> getSampleQuestions() {
        return sampleQuestions;
    }

    public void setSampleQuestions(List<String> sampleQuestions) {
        this.sampleQuestions = sampleQuestions == null ? new ArrayList<>() : sampleQuestions;
    }
}

package com.aidecision.agentic;

import com.aidecision.agentic.config.AzureOpenAiProperties;
import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.config.QaUiProperties;
import com.aidecision.agentic.config.RagApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AzureOpenAiProperties.class,
        OrchestratorProperties.class,
        RagApiProperties.class,
        QaUiProperties.class
})
public class AgenticApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticApplication.class, args);
    }
}

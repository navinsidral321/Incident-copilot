package com.company.copilot.config;

import com.company.copilot.tools.IncidentTriageTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class CopilotConfig {

    @Value("${services.incident-service.base-url:http://localhost:8081}")
    private String incidentServiceUrl;

    /**
     * Main ChatClient used for all AI interactions.
     * Configured with:
     *  - In-memory chat history (swap for Redis-backed in production)
     *  - Default system prompt
     *  - Tool callbacks registered
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                    new MessageChatMemoryAdvisor(new InMemoryChatMemory())
                )
                .build();
    }

    /**
     * Vector store for RAG — past incident similarity search.
     * Using SimpleVectorStore for local/demo mode.
     * Swap for PgVectorStore or RedisVectorStore in production:
     *
     *   @Bean public VectorStore vectorStore(JdbcTemplate jdbc, EmbeddingModel em) {
     *       return new PgVectorStore(jdbc, em);
     *   }
     */
    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * WebClient for calling incident-service REST API.
     */
    @Bean
    public WebClient incidentServiceClient(WebClient.Builder builder) {
        return builder
                .baseUrl(incidentServiceUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}

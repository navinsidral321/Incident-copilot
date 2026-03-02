package com.company.incident.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for incident-service.
 *
 * Responsibilities:
 *  - Configures ProducerFactory with exactly-once semantics (idempotent producer)
 *  - Creates the KafkaTemplate used by IncidentService.publishIncidentCreatedEvent()
 *  - Declares all 3 Kafka topics so they are auto-created on startup
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,    bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG,               "all"); // wait for all replicas
        props.put(ProducerConfig.RETRIES_CONFIG,              3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // exactly-once producer
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS,     false); // clean JSON — no Spring type headers
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Auto-create topics on startup ────────────────────────────────────────

    @Bean
    public NewTopic incidentCreatedTopic() {
        return TopicBuilder.name("incident.created")
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic logCorrelationRequestTopic() {
        return TopicBuilder.name("log.correlation.request")
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic logCorrelationResultTopic() {
        return TopicBuilder.name("log.correlation.result")
                .partitions(3).replicas(1).build();
    }
}

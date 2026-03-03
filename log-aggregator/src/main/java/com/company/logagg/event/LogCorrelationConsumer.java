package com.company.logagg.event;

import com.company.common.events.LogCorrelationRequestEvent;
import com.company.logagg.service.LogCorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogCorrelationConsumer {

    private final LogCorrelationService logCorrelationService;

    @KafkaListener(
        topics = LogCorrelationRequestEvent.TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCorrelationRequest(
            @Payload LogCorrelationRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack) {

        log.info("Received log correlation request for incident [{}], services={}",
                event.getIncidentId(), event.getServiceNames());

        try {
            logCorrelationService.correlateLogsForIncident(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to correlate logs for incident [{}]", event.getIncidentId(), ex);
            // Let Kafka retry based on consumer config
            throw ex;
        }
    }
}

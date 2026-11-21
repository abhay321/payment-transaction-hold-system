package com.abhay.payment.infrastructure.kafka;

import com.abhay.payment.infrastructure.persistence.OutboxEventPublisher;
import com.abhay.payment.infrastructure.persistence.OutboxMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "payment.events.kafka.enabled", havingValue = "true")
public final class KafkaOutboxEventPublisher implements OutboxEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    public KafkaOutboxEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                     @Value("${payment.events.topic}") String topic) {
        this.kafka = kafka; this.mapper = mapper; this.topic = topic;
    }
    @Override public void publish(OutboxMessage message) {
        try {
            kafka.send(topic, message.merchantId() + ":" + message.transactionId(),
                    mapper.writeValueAsString(message)).get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize outbox event", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Kafka did not acknowledge outbox event", ex);
        }
    }
}

package com.abhay.payment.infrastructure.kafka;

import com.abhay.payment.infrastructure.persistence.OutboxMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaOutboxEventPublisherIntegrationTest {
    @Test
    void publishesOutboxEnvelopeToLocalOpenSourceKafka() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))) {
            kafka.start();
            String topic = "payment-events-it";
            try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            }

            var producerProps = Map.<String, Object>of(
                    "bootstrap.servers", kafka.getBootstrapServers(),
                    "key.serializer", StringSerializer.class,
                    "value.serializer", StringSerializer.class,
                    "acks", "all",
                    "enable.idempotence", true);
            var producerFactory = new DefaultKafkaProducerFactory<String, String>(producerProps);
            try {
                KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
                KafkaOutboxEventPublisher publisher =
                        new KafkaOutboxEventPublisher(template, new ObjectMapper(), topic);
                UUID eventId = UUID.randomUUID();
                OutboxMessage message = new OutboxMessage(eventId, "merchant-a",
                        UUID.randomUUID(), "AmountCaptured", "{\"amount\":\"10.00\"}",
                        Instant.now(), 0);
                publisher.publish(message);

                Properties consumerProps = new Properties();
                consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-event-it");
                consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                    consumer.subscribe(List.of(topic));
                    var records = consumer.poll(Duration.ofSeconds(10));
                    assertThat(records).isNotEmpty();
                    assertThat(records.iterator().next().key()).isEqualTo("merchant-a:" + message.transactionId());
                    assertThat(new ObjectMapper().readTree(records.iterator().next().value())
                            .get("id").asText()).isEqualTo(eventId.toString());
                }
            } finally {
                producerFactory.destroy();
            }
        }
    }
}

package com.e_commerce.notificationservice.Service;

import com.e_commerce.notificationservice.Dto.UserCreatedValidationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DqlConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "outbox.user.user-created.DLT",
            groupId = "user-dlq-consumer-group",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void consumerDql(ConsumerRecord<String, String> record,
                            Acknowledgment ack,
                            // Cabecera inyectada por DeadLetterPublishingRecoverer
                            // con el nombre del topic original donde falló
                            @Header(value = "kafka_dlt-original-topic",
                                    required = false) String originalTopic,
                            // Número de partition original
                            @Header(value = "kafka_dlt-original-partition",
                                    required = false) byte[] originalPartition,
                            // El offset exacto donde falló en el topic original
                            @Header(value = "kafka_dlt-original-offset",
                                    required = false) byte[] originalOffset,
                            // El mensaje de la excepción que causó el fallo
                            @Header(value = "kafka_dlt-exception-message",
                                    required = false) String exceptionMessage) {

        UserCreatedValidationEvent event;

        try {
            JsonNode eventoJson = objectMapper.readTree(record.value());
            JsonNode nodo = eventoJson.get("payload").get("email");
            event = objectMapper.treeToValue(
                    eventoJson.get("payload"),
                    UserCreatedValidationEvent.class
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        log.error("""
            ══ MENSAJE EN DLQ ══
            userId: {}
            userEmail: {}
            topic original: {}
            excepción: {}
            original Partition: {}
            """,
                event.userId(),
                event.email(),
                originalTopic,
                exceptionMessage,
                originalPartition
        );

        ack.acknowledge();

    }
}

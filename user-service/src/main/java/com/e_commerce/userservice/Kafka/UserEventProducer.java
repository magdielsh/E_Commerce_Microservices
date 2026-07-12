package com.e_commerce.userservice.Kafka;

import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import com.e_commerce.userservice.Repository.OutboxEventRepository;
import com.e_commerce.userservice.Service.OutBoxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j  // Lombok genera: private static final Logger log = LoggerFactory.getLogger(...)
@Service
@RequiredArgsConstructor
public class UserEventProducer {


    // KafkaTemplate<K, V>:
    //   K = tipo de la clave del mensaje (String: usamos el orderId como clave)
    //   V = tipo del valor del mensaje (OrderCreatedEvent: se serializa a JSON)
    // Spring lo autoconfigura con los parámetros del application.yml
    private final KafkaTemplate<String, UserCreatedValidationEvent> kafkaTemplate;
    private final KafkaHealthChecker kafkaHealthChecker;
    private final OutBoxService outBoxService;

    // Nombre del topic de Kafka donde publicamos los eventos.
    // Convención: nombres en kebab-case, descriptivos del evento.
    private final String USER_TOPIC = "outbox.event.user-created";

    public void publishUserVerification(UserCreatedValidationEvent event) {
        // kafkaTemplate.send(topic, key, value):
        //   topic: el topic destino
        //   key:   la clave del mensaje (el userId como String)
        //          Kafka usa la clave para decidir en qué partición va el mensaje.
        //          Mensajes con la misma clave van a la misma partición → orden garantizado.
        //   value: el objeto que se serializa a JSON y se envía

        ProducerRecord<String, UserCreatedValidationEvent> record =
                new ProducerRecord<>(
                        USER_TOPIC,                      // topic destino
                        event.userId().toString(),       // clave = orderId (4)
                        event                            // valor = el evento completo
                );

        if (!kafkaHealthChecker.checkKafkaHealth()) {
            log.error("Error enviando mensaje {}, para el usuario: {}", event.userId(), event.email());
            //outBoxService.buildOutboxEvent(event);
            log.error("Guardando mensaje {}, para el usuario: {} en OutBox", event.userId(), event.email());
        } else {
            try {
                CompletableFuture<SendResult<String, UserCreatedValidationEvent>> future = kafkaTemplate.send(record);
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error enviando mensaje {}, para el usuario: {}, Error: {}", event.userId(), event.email(), ex.getMessage());
                       // outBoxService.buildOutboxEvent(event);
                        log.error("Guardando mensaje {}, para el usuario: {} en OutBox", event.userId(), event.email());
                    } else {
                        log.info("Orden {} → topic={} partición={} offset={}",
                                event.userId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        log.info("Evento publicado en Kafka: userId={}, email={}", event.userId(), event.email());
                    }
                });
            } catch (Exception ex) {
                log.error("Error enviando mensaje {}, para el usuario: {}, Error: {}", event.userId(), event.email(), ex.getMessage());
               // outBoxService.buildOutboxEvent(event);
                log.error("Guardando mensaje {}, para el usuario: {} en OutBox", event.userId(), event.email());
            }
        }
    }


}

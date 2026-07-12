package com.e_commerce.notificationservice.Service;

import com.e_commerce.notificationservice.Dto.UserCreatedValidationEvent;
import com.e_commerce.notificationservice.Exceptions.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final IdempotencyServiceRedis idempotencyServiceRedis;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base.url}")
    private String baseUrl;

    @KafkaListener(topics = "outbox.user.user-created", groupId = "notification-processing-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void sendVerificationEmail(ConsumerRecord<String, String> record,
                                      @Payload String messageeee,
                                      //Acknowledgment ack,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      @Header("id") byte[] idBytes) {

        log.info("📨 Mensaje recibido por NOTIFICATION → topic={}, partition={}, offset={}, key={}",
                record.topic(), partition, offset, record.key());

        // Capturar headers
        UUID messageID = UUID.fromString(new String(
                record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));


        if (!idempotencyServiceRedis.markAsProcessedIfAbsent(messageID.toString())) {
            log.warn("❌ Mensaje duplicado detectado, se ignora: {}", messageID);
            return;
        }

        try {
            JsonNode eventoJson = objectMapper.readTree(record.value());
            JsonNode nodo = eventoJson.get("payload").get("email");
            // Mapear payload (ignorar el wrapper "schema")
            UserCreatedValidationEvent event = objectMapper.treeToValue(
                    eventoJson.get("payload"),
                    UserCreatedValidationEvent.class
            );
            String verificationLink = baseUrl + "/v1/api/users/verify?token=" + event.verificationToken();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(event.email());
            message.setSubject("Verifica tu cuenta");
            message.setText(
                "Hola,\n\n" +
                        "Gracias por registrarte. Haz clic en el siguiente enlace para activar tu cuenta:\n\n" +
                        verificationLink + "\n\n" +
                        "Este enlace expira en 24 horas.\n\n" +
                        "Si no creaste esta cuenta, ignora este mensaje."
            );

            log.info("Email recibido: '{}'", event.email());

//            if (event.email().equalsIgnoreCase("pedrito@gmail.com")) {
//                log.info("LANZANDO BUSINESS-EXCEPTION");
//                throw new BusinessException(
//                        "Error transitorio al procesar Email: " + event.email());
//            }



            //mailSender.send(message);
            log.info(message.toString());
            //ack.acknowledge();



        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }catch (BusinessException ex) {          // (9)
            log.info("❌ Error transitorio, Kafka reintentará: {}", ex.getMessage());
            idempotencyServiceRedis.remove(messageID);
            throw ex;              // No hacemos ack → Kafka reentregará el mensaje
        } catch (NullPointerException ex) {              // (10)
            log.error("Error fatal para orden {}: {}", messageID, ex.getMessage());
            //ack.acknowledge();     // Hacemos ack para no bloquear, pero enviamos a DLQ
            sendToDlq(record);
        }

    }

    private void sendToDlq(ConsumerRecord<String, String> record) {
        // Reenvía el mensaje record al topic DLQ con metadata de error
    }



}

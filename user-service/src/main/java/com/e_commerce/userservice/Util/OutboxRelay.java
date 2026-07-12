//package com.e_commerce.userservice.Util;
//
//import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
//import com.e_commerce.userservice.Entity.OutboxEvent;
//import com.e_commerce.userservice.Repository.OutboxEventRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.kafka.clients.producer.ProducerRecord;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class OutboxRelay {
//
//    private final OutboxEventRepository outboxRepository;
//    private final KafkaTemplate<String, UserCreatedValidationEvent> kafkaTemplate;
//
//    private static final int BATCH_SIZE = 50;
//
//   // @Scheduled(fixedDelay = 5000)
//    @Transactional
//    public void relay() {
//        List<OutboxEvent> pending = outboxRepository.findByStatus(BATCH_SIZE);
//        if (pending.isEmpty()) return;
//
//        log.debug("Relay: procesando {} eventos pendientes", pending.size());
//
//        pending.forEach(this::publishAndUpdate);
//    }
//
//    private void publishAndUpdate(OutboxEvent outboxEvent) {
//        try {
//            var record = new ProducerRecord<String, UserCreatedValidationEvent>(
//                    outboxEvent.getTopic(),
//                    outboxEvent.getAggregateId(),
//                    outboxEvent.getPayload()
//            );
//
//            // Añadimos headers con metadata del evento                  (5)
//            record.headers()
//                    .add("eventType",      outboxEvent.getEventType().getBytes())
//                    .add("aggregateType",  outboxEvent.getAggregateType().getBytes())
//                    .add("outboxEventId",  outboxEvent.getId().toString().getBytes());
//
//            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
//
//            outboxEvent.markAsSent();                            // (7)
//            log.info("Evento {} publicado del OutBox en {}", outboxEvent.getId(), outboxEvent.getTopic());
//
//        } catch (Exception ex) {
//            outboxEvent.markAsFailed(ex.getMessage());           // (8)
//            log.error("Fallo publicando evento {}: {}", outboxEvent.getId(), ex.getMessage());
//        }
//        // (9) outboxEvent es una entidad gestionada por Hibernate:
//        // los cambios de markAsSent()/markAsFailed() se commitean solos al cerrar @Transactional
//    }
//}

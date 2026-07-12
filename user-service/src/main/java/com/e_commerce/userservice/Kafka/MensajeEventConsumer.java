package com.e_commerce.userservice.Kafka;

import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import com.e_commerce.userservice.Repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer de Kafka para los eventos del Outbox de mensajes.
 *
 * Escucha el topic donde Debezium publica los eventos capturados
 * de la tabla outbox_events. Cada mensaje es un evento de dominio
 * que este consumer debe procesar (notificaciones, caché, etc.)
 *
 * GARANTÍAS DE ENTREGA:
 * ─────────────────────
 * Con "at-least-once delivery" (enable-auto-commit: false + MANUAL_IMMEDIATE):
 * → Un mensaje puede llegar MÁS DE UNA VEZ (reintento tras crash).
 * → Tu lógica debe ser IDEMPOTENTE: procesar el mismo evento dos veces
 *   no debe causar efectos secundarios duplicados.
 *
 * Ejemplo de idempotencia: antes de enviar una notificación push,
 * verificar si ya se envió para ese mensajeId + eventType.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MensajeEventConsumer {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * @KafkaListener: registra este método como handler del topic.
     *
     * topics: el topic que Debezium crea con el OutboxEventRouter.
     *   Formato: {topicPrefix}.{aggregateType}
     *   → "outbox.Mensaje"
     *
     * groupId: heredado del application.yml (spring.kafka.consumer.group-id).
     *   Si hubiera múltiples consumers con distintos propósitos (notificaciones,
     *   analytics, etc.), cada uno usaría un groupId diferente.
     *
     * containerFactory: usa el ConcurrentKafkaListenerContainerFactory que
     *   configuramos en KafkaConfig (con backoff exponencial y MANUAL ack).
     */
    @KafkaListener(
            topics = "${app.kafka.topics.mensajes}",
            groupId = "user-service-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional  // El UPDATE del status en outbox_events va en transacción
    public void procesarEventoMensaje(
            ConsumerRecord<String, String> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header("id") byte[] idBytes) {

        // Log de trazabilidad: permite correlacionar logs entre el producer y el consumer
        log.info("📨 Evento recibido por USER-SERVICE → topic={}, partition={}, offset={}, key={}",
                record.topic(), partition, offset, record.key());

        // Capturar headers
        UUID ids = UUID.fromString(new String(
                record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));

        UUID id = UUID.fromString(new String(idBytes, StandardCharsets.UTF_8));
        System.out.println("UUID: " + ids);
        try {

            JsonNode eventoJson = objectMapper.readTree(record.value());
            JsonNode nodo = eventoJson.get("payload").get("email");
            // Mapear payload (ignorar el wrapper "schema")
            UserCreatedValidationEvent event = objectMapper.treeToValue(
                    eventoJson.get("payload"),
                    UserCreatedValidationEvent.class
            );
            outboxEventRepository.marcarComoProcesado(ids);
            ack.acknowledge();

           // System.out.println(eventoJson);
            //System.out.println(nodo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }



//        try {
//            // ── PASO 1: Deserializar el mensaje ───────────────────────────────────
//            // El valor del mensaje de Kafka es el JSON del payload del OutboxEvent.
//            // Con la transformación OutboxEventRouter de Debezium, el "value" del
//            // mensaje en Kafka ES directamente el campo "payload" de outbox_events.
//            JsonNode eventoJson = objectMapper.readTree(record.value());
//
//            // Extraemos los campos del evento
//            // El campo "event_type" en el JSON viene de outbox_events.event_type
//            String eventType = extraerCampo(eventoJson, "eventType");
//            Long mensajeId = eventoJson.has("mensajeId") ? eventoJson.get("mensajeId").asLong() : null;
//
//            log.debug("Procesando evento: type={}, mensajeId={}", eventType, mensajeId);
//
//            // ── PASO 2: Router por tipo de evento ─────────────────────────────────
//            // Cada tipo de evento tiene su propia lógica de procesamiento.
//            // Usamos constantes del servicio para evitar strings mágicos.
//            switch (eventType) {
//                case MensajeService.EVENTO_MENSAJE_CREADO   -> procesarMensajeCreado(eventoJson);
//                case MensajeService.EVENTO_MENSAJE_LEIDO    -> procesarMensajeLeido(eventoJson);
//                case MensajeService.EVENTO_MENSAJE_ELIMINADO -> procesarMensajeEliminado(eventoJson);
//                default -> log.warn("⚠️  Tipo de evento desconocido: {}. Ignorando.", eventType);
//            }
//
//            // ── PASO 3: Actualizar status en outbox_events ────────────────────────
//            // Marcamos el evento como procesado para:
//            //   1. Tener visibilidad de qué eventos se procesaron con éxito
//            //   2. Facilitar el job de limpieza (solo borra PROCESSED)
//            //
//            // El ID del evento viene en la KEY del mensaje de Kafka
//            // (Debezium usa outbox_events.id como key cuando usamos OutboxEventRouter)
//            if (record.key() != null) {
//                try {
//                    UUID outboxEventId = UUID.fromString(record.key());
//                    outboxEventRepository.marcarComoProcesado(outboxEventId);
//                } catch (IllegalArgumentException e) {
//                    // Si la key no es un UUID válido, continuamos sin marcar
//                    log.debug("Key del mensaje no es un UUID válido: {}", record.key());
//                }
//            }
//
//            // ── PASO 4: Confirmar el offset en Kafka ──────────────────────────────
//            // acknowledge() le dice a Kafka que este mensaje fue procesado con éxito.
//            // Kafka guardará el offset+1 como la posición actual del consumer.
//            // Si la app se cae ANTES de acknowledge(), Kafka reenvía el mensaje.
//            // Si se cae DESPUÉS, el mensaje no se reprocesa.
//            ack.acknowledge();
//            log.debug("✅ Offset confirmado: partition={}, offset={}", partition, offset);
//
//        } catch (Exception e) {
//            // NO hacemos ack.acknowledge() → Kafka reintentará el mensaje
//            // según la estrategia de backoff configurada en KafkaConfig.
//            log.error("❌ Error procesando evento. partition={}, offset={}, key={}: {}",
//                    partition, offset, record.key(), e.getMessage(), e);
//            // Relanzamos para que el DefaultErrorHandler aplique el backoff exponencial
//            throw new RuntimeException("Error procesando evento de Kafka", e);
//        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HANDLERS POR TIPO DE EVENTO
    //
    // Cada método se encarga de la lógica específica para su evento.
    // En un proyecto real, aquí invocarías otros servicios:
    //   - NotificacionService.enviarPush(...)
    //   - CacheService.invalidar(...)
    //   - BusquedaService.indexar(...)
    //   - EstadisticasService.incrementarContador(...)
    // ─────────────────────────────────────────────────────────────────────

    private void procesarMensajeCreado(JsonNode evento) {
        Long mensajeId = evento.get("mensajeId").asLong();
        String destinatario = extraerCampo(evento, "destinatario");
        String remitente = extraerCampo(evento, "remitente");

        log.info("✉️  MensajeCreado → id={}, de='{}' para='{}'",
                mensajeId, remitente, destinatario);

        /*
         * AQUÍ TU LÓGICA DE NEGOCIO:
         *
         * // 1. Notificación push al destinatario
         * notificacionService.enviarPush(destinatario,
         *     "Nuevo mensaje de " + remitente);
         *
         * // 2. Actualizar contador en Redis (para el badge)
         * cacheService.incrementarNoLeidos(destinatario);
         *
         * // 3. Indexar en Elasticsearch para búsqueda
         * busquedaService.indexarMensaje(mensajeId, evento);
         *
         * // 4. Emitir evento WebSocket al cliente conectado
         * websocketService.emit("/user/" + destinatario + "/mensajes", evento);
         */
    }

    private void procesarMensajeLeido(JsonNode evento) {
        Long mensajeId = evento.get("mensajeId").asLong();
        String destinatario = extraerCampo(evento, "destinatario");

        log.info("👁️  MensajeLeido → id={}, destinatario='{}'", mensajeId, destinatario);

        /*
         * AQUÍ TU LÓGICA DE NEGOCIO:
         *
         * // Decrementar contador de no leídos en Redis
         * cacheService.decrementarNoLeidos(destinatario);
         *
         * // Notificar al remitente que su mensaje fue leído (doble tick)
         * String remitente = extraerCampo(evento, "remitente");
         * websocketService.emit("/user/" + remitente + "/leidos", mensajeId);
         */
    }

    private void procesarMensajeEliminado(JsonNode evento) {
        Long mensajeId = evento.get("mensajeId").asLong();

        log.info("🗑️  MensajeEliminado → id={}", mensajeId);

        /*
         * AQUÍ TU LÓGICA DE NEGOCIO:
         *
         * // Eliminar de Elasticsearch
         * busquedaService.eliminarMensaje(mensajeId);
         *
         * // Invalidar caché
         * cacheService.invalidarMensaje(mensajeId);
         */
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILIDADES
    // ─────────────────────────────────────────────────────────────────────

    private String extraerCampo(JsonNode json, String campo) {
        JsonNode nodo = json.get(campo);
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        return nodo.asText();
    }
}

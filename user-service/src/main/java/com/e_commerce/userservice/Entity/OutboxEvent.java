package com.e_commerce.userservice.Entity;

import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    /**
     * Tipo de agregado de dominio.
     * Debezium OutboxEventRouter usa este campo para determinar
     * a qué topic de Kafka enviar el evento.
     *
     * Convención: nombre de la clase de dominio en PascalCase.
     * Ejemplo: "Mensaje", "Usuario", "Pedido"
     *
     * Topic resultante: {topicPrefix}.{aggregateType}
     *                   → "outbox.Mensaje"
     */
    @Column(name = "aggregate_type", nullable = false, length = 255)
    private String aggregateType;

    /**
     * ID del agregado que generó el evento.
     * Se convierte en la KEY del mensaje en Kafka.
     *
     * KEY en Kafka garantiza que todos los eventos del MISMO agregado
     * van a la MISMA PARTICIÓN, preservando el orden causal:
     * "MensajeCreado" siempre llega antes que "MensajeLeido" para el mensaje #42.
     */
    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    /**
     * Tipo específico del evento de dominio.
     * El consumer usa este campo como discriminador para saber
     * qué handler invocar.
     *
     * Convención: {Agregado}{Acción}
     * Ejemplos: "MensajeCreado", "MensajeLeido", "MensajeEliminado"
     */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    /**
     * Payload del evento en formato JSON.
     *
     * @JdbcTypeCode(SqlTypes.JSON): le dice a Hibernate que este campo
     * es JSONB en PostgreSQL. Hibernate serializa/deserializa el String
     * como JSON nativo, aprovechando las capacidades JSONB de PostgreSQL
     * (índices GIN, queries con operadores ->, ->>, @>, etc.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private UserCreatedValidationEvent payload;

    /**
     * Estado del evento en el ciclo de vida del outbox.
     * Almacenado como String (no enum) para flexibilidad en consultas SQL.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private Instant processedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    private String errorMessage;

    @Column(unique = true)
    private String idempotencyKey;

    // ── Constructor de fábrica: la única forma de crear un OutboxEvent ──
    public static OutboxEvent create(                // (3)
                                                     UUID id,
                                                     String aggregateType,
                                                     String aggregateId,
                                                     String eventType,
                                                     UserCreatedValidationEvent payload,
                                                     String idempotencyKey
    ) {
        var event = new OutboxEvent();
        event.id              = id;
        event.aggregateType   = aggregateType;
        event.aggregateId     = aggregateId;
        event.eventType       = eventType;
        event.payload         = payload;
        event.idempotencyKey  = idempotencyKey;
        return event;
    }

    // ── Métodos de transición de estado (Tell Don't Ask) ──
    public void markAsSent() {
        this.status      = OutboxStatus.SENT;
        this.processedAt = Instant.now();
    }

    public void markAsFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        if (this.retryCount >= 3) {
            this.status = OutboxStatus.FAILED;
        }
        // Si retryCount < 3 sigue en PENDING → el relay lo reintentará
    }

    public enum OutboxStatus { PENDING, SENT, FAILED }
}

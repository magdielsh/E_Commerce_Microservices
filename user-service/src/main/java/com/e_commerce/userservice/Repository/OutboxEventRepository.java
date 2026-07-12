package com.e_commerce.userservice.Repository;

import com.e_commerce.userservice.Entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);

    /**
     * Busca eventos PENDING creados antes de una fecha.
     * Si un evento lleva más de X minutos en PENDING, algo va mal.
     */
    List<OutboxEvent> findByStatusAndCreatedAtBefore(
            OutboxEvent.OutboxStatus status,
            ZonedDateTime fecha
    );

    /**
     * Actualiza el status de un evento a PROCESSED.
     * El consumer de Kafka llama a este método después de procesar el evento con éxito.
     *
     * Hacemos UPDATE directo en lugar de cargar la entidad + modificar + save()
     * para evitar una SELECT innecesaria (más eficiente).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent o SET o.status = 'SENT' WHERE o.id = :id")
    void marcarComoProcesado(@Param("id") UUID id);

    /**
     * Limpia eventos procesados más antiguos de X días.
     * Se ejecuta periódicamente (ver OutboxCleanupJob) para evitar
     * que la tabla crezca indefinidamente.
     *
     * En producción, considera archivar en lugar de borrar (para auditoría).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PROCESSED' AND o.createdAt < :fecha")
    int eliminarProcesadosAnterioresA(@Param("fecha") ZonedDateTime fecha);

    /**
     * Cuenta eventos por status y fecha.
     * Útil para métricas y dashboards de monitorización.
     */
    @Query("""
        SELECT COUNT(o) FROM OutboxEvent o
        WHERE o.status = :status
        AND o.createdAt < :threshold
        """)
    long countStuckEvents(@Param("threshold") Instant threshold, @Param("status") String status);
}

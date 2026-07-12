package com.e_commerce.orderservice.Feing;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración TEMPORAL de diagnóstico.
 *
 * Objetivo: confirmar si ProductNotFoundException llega al CircuitBreaker
 * como tal, o si algo la envuelve (Feign, un ErrorDecoder, Retry, etc.)
 * antes de que el aspecto del CircuitBreaker la evalúe contra ignoreExceptions.
 *
 * Se suscribe a los eventos de TODOS los CircuitBreaker registrados
 * (existentes y futuros) y loguea la clase EXACTA (getClass().getName())
 * de la excepción que el CB recibió, más su cadena de causas.
 *
 * IMPORTANTE: quitar esta clase (o al menos los listeners) una vez
 * terminado el diagnóstico; onError/onIgnoredError se disparan en
 * cada llamada y con mucho tráfico esto genera ruido y overhead de logging.
 */
@Configuration
public class CircuitBreakerDiagnosticConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerDiagnosticConfig.class);

    public CircuitBreakerDiagnosticConfig(CircuitBreakerRegistry registry) {

        // Cubre los CircuitBreaker que YA existen al arrancar el contexto
        registry.getAllCircuitBreakers().forEach(this::registrarListeners);

        // Cubre los CircuitBreaker que se creen DESPUÉS (ej. lazy, por-instancia con OpenFeign)
        registry.getEventPublisher()
                .onEntryAdded(entryAddedEvent -> registrarListeners(entryAddedEvent.getAddedEntry()));
    }

    private void registrarListeners(CircuitBreaker circuitBreaker) {

        circuitBreaker.getEventPublisher()

                // Se disparó cuando el CB CONTÓ la excepción como fallo real
                // (es decir: NO matcheó con ignoreExceptions)
                .onError(event -> {
                    Throwable t = event.getThrowable();
                    log.error("[CB={}] >>> REGISTRADA COMO FALLO. Clase real recibida: {}",
                            circuitBreaker.getName(), t.getClass().getName());
                    logCadenaCausas(circuitBreaker.getName(), t);
                })

                // Se disparó cuando el CB SÍ reconoció la excepción como ignorable
                .onIgnoredError(event -> {
                    Throwable t = event.getThrowable();
                    log.warn("[CB={}] >>> IGNORADA correctamente. Clase real recibida: {}",
                            circuitBreaker.getName(), t.getClass().getName());
                })

                .onSuccess(event ->
                        log.debug("[CB={}] Llamada exitosa.", circuitBreaker.getName()))

                .onStateTransition(event ->
                        log.info("[CB={}] Transición de estado: {} -> {}",
                                circuitBreaker.getName(),
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
    }

    /**
     * Recorre la cadena de causas (getCause()) para ver si en algún
     * nivel más profundo SÍ está la excepción original de negocio.
     * Esto confirma si hay wrapping y en qué capa ocurre.
     */
    private void logCadenaCausas(String cbName, Throwable throwable) {
        Throwable current = throwable.getCause();
        int nivel = 1;
        while (current != null) {
            log.error("[CB={}]     causa nivel {}: {} -> mensaje: {}",
                    cbName, nivel, current.getClass().getName(), current.getMessage());
            current = current.getCause();
            nivel++;
        }
    }
}

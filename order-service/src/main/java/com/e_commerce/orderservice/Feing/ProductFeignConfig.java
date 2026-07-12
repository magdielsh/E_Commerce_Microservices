package com.e_commerce.orderservice.Feing;


import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════════════
 * CONFIGURACIÓN ESPECÍFICA DEL CLIENTE FEIGN (products-service)
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * ⚠️ REGLA CRÍTICA: Esta clase NO lleva @Configuration.
 * <p>
 * Si llevas @Configuration:
 * → Spring la incluye en el ApplicationContext global.
 * → Los @Bean que define se aplican a TODOS los FeignClients.
 * → Tu ErrorDecoder para products-service también actuaría en otros clientes.
 * <p>
 * Sin @Configuration:
 * → Solo la aplicas explícitamente via @FeignClient(configuration = ProductFeignConfig.class).
 * → Los @Bean son locales a ese cliente.
 * <p>
 * Esta clase define:
 * 1. ErrorDecoder: mapea HTTP status → excepciones de dominio
 * 2. Retryer: desactivamos el retry nativo de Feign (usamos Resilience4j)
 * 3. Logger.Level: nivel de log para este cliente
 * 4. RequestInterceptor: propaga headers automáticamente (JWT, correlation-id, etc.)
 */
@Slf4j
public class ProductFeignConfig {

    /**
     * ──────────────────────────────────────────────────────────────
     * ERROR DECODER: Transforma errores HTTP en excepciones de dominio
     * ──────────────────────────────────────────────────────────────
     * <p>
     * Feign invoca este decoder cuando la respuesta HTTP es 4xx o 5xx.
     * Sin ErrorDecoder, Feign lanza FeignException genérica que mezcla
     * lógica HTTP con lógica de negocio.
     * <p>
     * Con ErrorDecoder:
     * HTTP 404 → ProductNotFoundException (negocio claro)
     * HTTP 409 → StockConflictException (negocio claro)
     * HTTP 503 → RetryableException (el CB/Retry lo reintentará)
     * HTTP 5xx → ServiceException (el CB lo registra como fallo)
     *
     * @Bean: registra este objeto como Bean dentro del contexto de este FeignClient.
     */
    @Bean
    public ErrorDecoder productErrorDecoder(ObjectMapper objectMapper) {
        return new ErrorDecoder() {

            /**
             * decode(String methodKey, Response response):
             *   methodKey → "ProductClient#findProductById(Long)"
             *               identifica qué método del cliente falló
             *   response  → la respuesta HTTP completa (status, headers, body)
             */
            @Override
            public Exception decode(String methodKey, Response response) {

                // Intentamos leer el body del error (el JSON que devuelve GlobalExceptionHandler)
                String errorBody = readBody(response, objectMapper);

                log.info("[ErrorDecoder] {} → HTTP {} | body: {}",
                        methodKey, response.status(), errorBody);

                Exception ex = new Exception();
                // Mapeamos el status HTTP a la excepción de dominio correcta
               switch (response.status()) {

                    case 404:
                        // products-service devolvió: { "error": "NOT_FOUND", "message": "Producto no encontrado..." }
                        // Lo convertimos en nuestra excepción de dominio local
                        log.info("[FeingConfig] - Entre aquiiii....ProductNotFoundException");
                        ex = new OrderExceptions.ProductNotFoundException(
                                extractMessage(errorBody, objectMapper)
                        );


                    case 409:
                            // products-service devolvió: { "error": "INSUFFICIENT_STOCK", ... }
                                new OrderExceptions.StockConflictException(
                                        extractMessage(errorBody, objectMapper)
                                );

                    case 400: new OrderExceptions.BadRequestException(
                                "Solicitud inválida a products-service: " + errorBody
                        );

                    case 503, 502, 504:
                            /*
                             * RetryableException: le indica a Feign y a Resilience4j
                             * que ESTE ERROR ES TRANSITORIO y se puede reintentar.
                             *
                             * El Circuit Breaker lo registra como fallo.
                             * Si hay Retry configurado, lo reintenta antes de abrir el CB.
                             *
                             * Parámetros de RetryableException:
                             *   - status: código HTTP
                             *   - message: descripción del error
                             *   - retryAfter: Date (null = reintentar inmediatamente)
                             *   - httpMethod: método HTTP de la request original
                             *   - request: la request original
                             */
                                new RetryableException(
                                        response.status(),
                                        "products-service no disponible (HTTP " + response.status() + ")",
                                        response.request().httpMethod(),
                                        (Long) null,
                                        response.request()
                                );

                    case 500:
                            // Error interno del servidor → no es retryable (probablemente un bug)
                            // El CB lo registrará como fallo
                                new OrderExceptions.ServiceException(
                                        "Error interno en products-service: " + errorBody
                                );

                    default:
                            // Cualquier otro error inesperado
                                new OrderExceptions.ServiceException(
                                        "Error inesperado en products-service (HTTP " + response.status() + "): " + errorBody
                                );
                };

               return ex;
            }

            /** Lee el body de la respuesta HTTP como String */
            private String readBody(Response response, ObjectMapper mapper) {
                try {
                    if (response.body() != null) {
                        return new String(
                                response.body().asInputStream().readAllBytes(),
                                StandardCharsets.UTF_8
                        );
                    }
                } catch (IOException e) {
                    log.warn("No se pudo leer el body del error: {}", e.getMessage());
                }
                return "";
            }

            /** Extrae el campo 'message' del JSON de error de products-service */
            @SuppressWarnings("unchecked")
            private String extractMessage(String body, ObjectMapper mapper) {
                try {
                    Map<String, Object> map = mapper.readValue(body, Map.class);
                    return (String) map.getOrDefault("message", body);
                } catch (Exception e) {
                    return body; // si no es JSON, devuelve el body crudo
                }
            }
        };
    }

    /**
     * ──────────────────────────────────────────────────────────────
     * RETRYER: Desactivamos el retry nativo de Feign
     * ──────────────────────────────────────────────────────────────
     * <p>
     * Feign tiene su propio sistema de reintentos (Retryer.Default).
     * Resilience4j también tiene Retry.
     * <p>
     * Si ambos están activos:
     * 3 reintentos Feign × 3 reintentos Resilience4j = 9 intentos reales
     * → el servidor recibe 9 veces la petición en lugar de 3.
     * <p>
     * Solución: desactivar el Retryer de Feign y dejar solo Resilience4j.
     */
    @Bean
    public Retryer feignRetryer() {
        // NEVER_RETRY: Feign no reintenta internamente.
        // Resilience4j Retry se encarga de ello.
        // Si dejamos el Retryer.Default(100, 1s, 3) activo junto a
        // resilience4j.retry.maxAttempts=3:
        //   → 3 reintentos Feign × 3 reintentos Resilience4j = 9 peticiones reales
        //   → El CB recibe 9 llamadas cuando esperabas 3.
        // SIEMPRE desactiva uno de los dos.
        return Retryer.NEVER_RETRY;
    }

    /**
     * ──────────────────────────────────────────────────────────────
     * LOGGER LEVEL: Nivel de log para este cliente específico
     * ──────────────────────────────────────────────────────────────
     * <p>
     * Este Bean sobreescribe el loggerLevel del application.yml para este cliente.
     * <p>
     * Logger.Level.BASIC:
     * → Loguea: método, URL, status code, tiempo de respuesta.
     * → Ejemplo: [ProductClient#findProductById] GET http://localhost:8081/products/1 HTTP/1.1
     * [ProductClient#findProductById] <-- 200 (45ms)
     * <p>
     * También necesitas configurar el nivel del logger en application.yml:
     * logging.level.com.demo.orders.client: DEBUG
     * (sin esto, los logs de Feign no aparecen aunque el nivel sea FULL)
     */
    @Bean
    public Logger.Level feignLogLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * ──────────────────────────────────────────────────────────────
     * REQUEST INTERCEPTOR: Propaga headers de la petición entrante
     * ──────────────────────────────────────────────────────────────
     * <p>
     * Escenario: orders-service recibe una petición con header Authorization: Bearer JWT_TOKEN
     * Queremos que ese mismo token se propague a products-service automáticamente.
     * <p>
     * Sin este interceptor: Feign enviaría la petición SIN el header Authorization.
     * products-service devolvería 401 Unauthorized.
     * <p>
     * El interceptor se ejecuta ANTES de cada petición Feign.
     */
    @Bean
    public feign.RequestInterceptor authPropagationInterceptor() {
        return requestTemplate -> {
            /*
             * RequestContextHolder: accede al contexto de la petición HTTP actual.
             * Funciona porque Spring MVC guarda cada petición en un ThreadLocal.
             *
             * ⚠️  No funciona en llamadas asíncronas (@Async) ni en hilos secundarios
             *     sin propagación del contexto (InheritableThreadLocalRequestAttributes).
             */
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                var request = attributes.getRequest();

                // 1. Propagar JWT
                String authorization = request.getHeader("Authorization");
                if (authorization != null && !authorization.isBlank()) {
                    requestTemplate.header("Authorization", authorization);
                    log.info("[Feign Interceptor] Propagando Authorization header");
                }

                // 2. Propagar Correlation ID para tracing distribuido manual
                //    (complementa Micrometer Tracing que lo hace automáticamente)
                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId != null) {
                    requestTemplate.header("X-Correlation-Id", correlationId);
                }

                // 3. Propagar el Accept-Language para respuestas internacionalizadas
                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null) {
                    requestTemplate.header("Accept-Language", acceptLanguage);
                }
            }
        };
    }
}
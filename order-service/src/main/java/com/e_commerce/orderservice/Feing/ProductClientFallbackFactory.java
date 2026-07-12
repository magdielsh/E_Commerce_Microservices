package com.e_commerce.orderservice.Feing;

import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════
 * FALLBACK FACTORY: Respuesta alternativa cuando el CB está OPEN
 * ══════════════════════════════════════════════════════════════════════
 * <p>
 * FallbackFactory<ProductClient>:
 * → Patrón Factory que crea una implementación alternativa de ProductClient.
 * → Se diferencia del @FeignClient(fallback = ...) básico en que:
 * ✅ Tiene acceso a la excepción (Throwable cause) que causó el fallo.
 * ✅ Puede loguear el error específico.
 * ✅ Puede devolver distintas respuestas según el tipo de error.
 * ✅ Puede relanzar ciertas excepciones en lugar de absorberlas.
 * <p>
 * Cuándo se activa el fallback:
 * 1. El Circuit Breaker está en estado OPEN.
 * 2. Ocurre un error durante la llamada (timeout, 5xx, IOException).
 * 3. El TimeLimiter supera el timeout configurado.
 * <p>
 * Cuándo NO se activa:
 * 1. Los errores ignorados por el CB (404 ProductNotFoundException).
 * 2. Si circuitbreaker.enabled=false en application.yml.
 *
 * @Component: necesario para que Spring lo encuentre y lo inyecte en @FeignClient.
 * @Slf4j: logger para registrar qué causó la activación del fallback.
 */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    /**
     * create(Throwable cause):
     * → Spring llama este método cada vez que el fallback es necesario.
     * → 'cause' es la excepción real que provocó el fallo:
     * - feign.RetryableException: timeout o error de conexión
     * - feign.FeignException.InternalServerError: 500 del servidor
     * - feign.FeignException.ServiceUnavailable: 503
     * - io.github.resilience4j.circuitbreaker.CallNotPermittedException: CB abierto
     * <p>
     * → Devolvemos una implementación anónima de ProductClient.
     */
    @Override
    public ProductClient create(Throwable cause) {

        // Logueamos la causa para monitoring/alerting
        log.error("[FALLBACK] Ha ocurrido un error con Product-Service. Causa: {} - {}",
                cause.getClass().getSimpleName(),
                cause.getMessage());

        // Implementación anónima de ProductClient → cada método devuelve
        // una respuesta "segura" que no rompe el flujo de orders-service
        return new ProductClient() {

            /**
             * Fallback de findAllProducts:
             * Devuelve lista vacía en lugar de propagar el error.
             * orders-service puede continuar (mostrará catálogo vacío).
             */
            @Override
            public List<ProductDTO.Response> findAllProducts(String category) {
                log.warn("[FALLBACK] findAllProducts(category={}) → lista vacía", category);
                return List.of();  // List.of() crea una lista inmutable vacía
            }

            /**
             * Fallback de findProductById:
             * Devuelve un producto "fantasma" con datos mínimos.
             *
             * Alternativa válida: lanzar una excepción de negocio.
             * Elegimos según el contrato del API:
             *   - Si la orden PUEDE continuar sin el producto → devuelve vacío
             *   - Si la orden NO puede continuar sin el producto → lanza excepción
             *
             * Aquí optamos por devolver datos vacíos para mostrar cómo funciona.
             * En un e-commerce real, probablemente lanzarías una excepción.
             */
            @Override
            public ProductDTO.Response getProductById(Long id) {
                log.warn("[FALLBACK] findProductById(id={}) → producto vacío", id);
                log.info("CAUSA: " + cause);
                // Detectamos si el CB está abierto vs. si fue un error puntual
                if (cause.getCause() instanceof feign.RetryableException) {
                    log.info("[FALLBACK] Timeout alcanzado buscando producto {}", id);
                }

                // Producto "fantasma" con valores seguros
                return ProductDTO.Response.builder()
                        .id(id)
                        .name("Producto temporalmente no disponible")
                        .description("Este producto en estos momentos no existe. Intenete mas tarde.")
                        .price(BigDecimal.ZERO)
                        .stockQuantity(0)
                        .category("UNKNOWN")
                        .available(false)  // flag que indica que es un fallback
                        .exception(cause)
                        .build();
            }

            /**
             * Fallback de updateProductStock:
             * NO podemos "simular" una actualización de stock — es una operación
             * con efecto secundario real. Si falla, DEBEMOS informar al caller.
             *
             * Estrategia: lanzar una excepción de negocio para que OrderService
             * pueda marcar la orden como PENDIENTE y reintentarla después
             * (patrón Outbox / compensación del SAGA).
             */
            @Override
            public ProductDTO.Response updateProductStock(Long productId, StockUpdateRequest request) {
                log.error("[FALLBACK] updateProductStock(productId={}) → NO se puede actualizar stock", productId);

                // Relanzamos como excepción de negocio para que OrderService la maneje
                throw new OrderExceptions.StockUpdateFailedException(
                        productId,
                        "El servicio de productos no está disponible. Stock no actualizado."
                );
            }

            /**
             * Fallback de checkHealth:
             * Simple respuesta indicando que el servicio está caído.
             */
            @Override
            public String checkHealth() {
                return "products-service DOWN (fallback activo - Circuit Breaker: "
                        + cause.getClass().getSimpleName() + ")";
            }
        };
    }
}

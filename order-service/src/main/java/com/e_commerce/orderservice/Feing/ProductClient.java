package com.e_commerce.orderservice.Feing;

import com.e_commerce.orderservice.DTOs.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════
 * EL CORAZÓN DE FEIGN: La interfaz @FeignClient
 * ══════════════════════════════════════════════════════════════════════
 *
 * Esta INTERFAZ es todo lo que necesitas para hacer llamadas HTTP.
 * No hay implementación — Spring genera el código automáticamente.
 *
 * @FeignClient parámetros:
 *
 *   name = "products-service"
 *     → Identificador único del cliente. Dos usos:
 *       1. Es la clave para buscar la configuración en application.yml
 *          (resilience4j.circuitbreaker.instances.products-service)
 *       2. Con un Service Registry (Eureka/Consul), es el nombre del servicio
 *          para hacer resolución de DNS dinámica (load balancing automático).
 *          En ese caso NO necesitas 'url'.
 *
 *   url = "${services.products.url}"
 *     → URL base hardcodeada. Solo se usa cuando NO tienes Service Registry.
 *       ${services.products.url} lee el valor de application.yml:
 *         services.products.url: http://localhost:8081
 *       En producción con Kubernetes, esto sería http://products-service:8081
 *
 *   fallbackFactory = ProductClientFallbackFactory.class
 *     → Clase que se invoca cuando el Circuit Breaker está OPEN o hay error.
 *       FallbackFactory (vs fallback simple) porque nos da acceso a la excepción
 *       que causó el fallo → podemos loguear, distinguir errores, etc.
 *
 *   configuration = ProductFeignConfig.class
 *     → Configuración específica para ESTE cliente (ErrorDecoder, interceptores, etc.)
 *       ⚠️ La clase de configuración NO debe tener @Configuration para no aplicarse globalmente.
 */
@FeignClient(
        name = "products-service",
        //url = "${services.products.url}",
        fallbackFactory = ProductClientFallbackFactory.class,
        configuration = ProductFeignConfig.class
)
public interface ProductClient {


    /**
     * GET http://localhost:8081/products
     * GET http://localhost:8081/products?category=ELECTRONICS
     *
     * @GetMapping("/products"):
     *   → Feign construye: GET {url_base}/products
     *
     * @RequestParam(required = false):
     *   → Si category es null, NO se añade al query string.
     *   → Si category = "ELECTRONICS": GET /products?category=ELECTRONICS
     *
     * List<ProductDTO.Response>:
     *   → Feign deserializa el JSON array de la respuesta a esta lista.
     *   → Jackson usa el ProductDTO.Response del paquete orders (no el de products).
     *     ¡IMPORTANTE! Los campos deben coincidir en nombre y tipo.
     */
    @GetMapping("v1/api/products")
    List<ProductDTO.Response> findAllProducts(
            @RequestParam(value = "category", required = false) String category
    );

    /**
     * GET http://localhost:8081/products/{id}
     *
     * @PathVariable("id"):
     *   → Sustituye {id} en la URL con el valor del parámetro.
     *   → ⚠️ En Feign (a diferencia de Spring MVC), el nombre entre comillas
     *     es OBLIGATORIO: @PathVariable("id") Long id
     *     Si omites el nombre → IllegalStateException al arrancar.
     *
     * Casos que maneja Feign automáticamente:
     *   - 200 OK → deserializa a ProductDTO.Response ✅
     *   - 404 NOT FOUND → ErrorDecoder → ProductNotFoundException ✅
     *   - 500 SERVER ERROR → ErrorDecoder → ServiceException → CB cuenta fallo ✅
     *   - Timeout → RetryableException → Retry → CB cuenta fallo → fallback ✅
     */
    @GetMapping("v1/api/products/{id}")
    ProductDTO.Response getProductById(@PathVariable("id") Long id);

    /**
     * PATCH http://localhost:8081/products/{id}/stock
     * Body: { "quantity": -2 }
     *
     * @RequestBody:
     *   → Feign serializa el objeto Java a JSON y lo envía en el body.
     *   → Jackson añade automáticamente Content-Type: application/json
     *
     * void:
     *   → No deserializamos la respuesta. Solo nos importa que no haya error.
     *   → Si hay error → ErrorDecoder lo maneja.
     *
     * Este método lo llamamos cuando una orden es confirmada para descontar stock.
     */
    @PatchMapping("v1/api/products/{id}/stock")
    ProductDTO.Response updateProductStock(
            @PathVariable("id") Long productId,
            @RequestBody StockUpdateRequest request
    );

    /**
     * GET http://localhost:8081/products/health-check
     *
     * Verificar si products-service está disponible antes de procesar órdenes.
     * Si el CB está OPEN, el fallback devolverá "DOWN" sin llamar al servicio.
     */
    @GetMapping("v1/api/products/health-check")
    String checkHealth();

    /**
     * DTO local: body del PATCH /products/{id}/stock
     *
     * Lo definimos DENTRO del cliente porque solo lo necesita Feign.
     * Alternativa: podríamos compartir un módulo 'api-contracts' entre servicios.
     */
    record StockUpdateRequest(Integer quantity) {}
}

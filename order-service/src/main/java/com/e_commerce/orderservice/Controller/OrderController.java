package com.e_commerce.orderservice.Controller;

import com.e_commerce.orderservice.DTOs.OrderDTO;
import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.e_commerce.orderservice.Service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del microservicio de Órdenes.
 *
 * ENDPOINTS:
 *   POST   /orders                     → crear orden (usa Feign internamente)
 *   GET    /orders                     → listar todas las órdenes
 *   GET    /orders/{id}                → obtener orden por ID
 *   GET    /orders/products            → ver catálogo (prueba Feign)
 *   GET    /orders/products/{id}       → ver producto específico (prueba Feign)
 *   GET    /orders/circuit-breaker     → estado del Circuit Breaker (prueba CB)
 */
@Slf4j
@RestController
@RequestMapping("v1/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /orders
     * Body: { "customerId": "client-1", "items": [{ "productId": 1, "quantity": 2 }] }
     *
     * Flujo interno:
     *   1. Valida el DTO (@Valid)
     *   2. OrderService.createOrder() → Feign consulta y descuenta stock
     *   3. 201 CREATED con la orden confirmada
     *   4. Si algo falla → GlobalExceptionHandler → 4xx/5xx con JSON de error
     */
    @PostMapping("/createOrder")
    public ResponseEntity<OrderDTO.Response> createOrder(
            @Valid @RequestBody OrderDTO.CreateRequest request) {

        log.info("POST /orders - cliente: {}, productos: {}",
                request.getCustomerId(), request.getItems().size());

        OrderDTO.Response created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /orders
     * Lista todas las órdenes (sin paginación para simplificar el demo).
     */
    @GetMapping
    public ResponseEntity<List<OrderDTO.Response>> getAllOrders() {
        return ResponseEntity.ok(orderService.findAll());
    }

    /**
     * GET /orders/{id}
     * Obtiene una orden por su ID.
     * Si no existe → OrderNotFoundException → GlobalExceptionHandler → 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO.Response> getOrderById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    /**
     * GET /orders/products
     * GET /orders/products?category=ELECTRONICS
     *
     * Endpoint de conveniencia para probar la llamada Feign sin crear una orden.
     * Demuestra:
     *   - Llamada normal → lista de productos de products-service
     *   - CB abierto     → lista vacía del fallback
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductDTO.Response>> getProducts(
            @RequestParam(required = false) String category) {

        log.info("GET /orders/products - category={} (via Feign)", category);
        return ResponseEntity.ok(orderService.getAvailableProducts(category));
    }

    /**
     * GET /orders/products/{productId}
     *
     * Consulta un producto específico vía Feign.
     * Útil para probar:
     *   - 200 OK: productId existente (1, 2, 3, 4)
     *   - 404: productId inexistente (99)
     *   - Fallback: con products-service caído
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDTO.Response> getProduct(
            @PathVariable("productId") Long productId) {

        log.info("GET /orders/products/{} (via Feign)", productId);

        // Llamada Feign directa al service
        var product = orderService.getAvailableProducts(null)
                .stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .orElseGet(() -> {
                    // Si no está en la lista, consultamos directamente
                    // Esto puede lanzar ProductNotFoundException (404)
                    return null;
                });

        // Usamos el OrderService que llama al ProductClient
        // Para demostrar el flujo completo usamos el método de health check
        return ResponseEntity.ok(orderService.getAvailableProducts(null)
                .stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .orElseThrow(() ->
                        new OrderExceptions.ProductNotFoundException(productId)));
    }

    /**
     * GET /orders/health
     *
     * Verifica el estado de products-service a través del FeignClient.
     * Respuestas posibles:
     *   - "products-service is UP"  → servicio disponible, CB cerrado
     *   - "products-service DOWN (fallback activo - Circuit Breaker: ...)" → CB abierto
     *
     * Úsalo para probar el Circuit Breaker:
     *   1. Apaga products-service
     *   2. Llama a este endpoint varias veces → verás cómo el CB se abre
     *   3. Rearranca products-service
     *   4. Espera 10s (waitDurationInOpenState) → CB pasa a HALF-OPEN → CLOSED
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> checkHealth() {
        String productsHealth = orderService.checkProductsServiceHealth();
        return ResponseEntity.ok(Map.of(
                "orders-service", "UP",
                "products-service", productsHealth
        ));
    }
}

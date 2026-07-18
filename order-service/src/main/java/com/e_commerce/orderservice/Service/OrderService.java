package com.e_commerce.orderservice.Service;

import com.e_commerce.orderservice.DTOs.OrderDTO;
import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Entity.OrderEntity;
import com.e_commerce.orderservice.Entity.OrderItem;
import com.e_commerce.orderservice.Enums.EStatus;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.e_commerce.orderservice.Feing.ProductClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Capa de negocio del microservicio de Órdenes.
 * <p>
 * Aquí es donde Feign realmente brilla: productClient se usa como si fuera
 * un repositorio local, pero internamente hace llamadas HTTP a products-service.
 * <p>
 * Flujo de createOrder:
 * 1. Para cada producto en la orden:
 * a. Llamar a productClient.findProductById(id) → GET /products/{id}
 * b. Verificar que el producto existe y tiene stock
 * c. Calcular subtotal (precio × cantidad)
 * 2. Crear la orden en estado PENDING
 * 3. Para cada producto:
 * a. Llamar a productClient.updateProductStock(id, -cantidad) → PATCH /products/{id}/stock
 * 4. Actualizar orden a CONFIRMED
 * 5. Si algo falla → orden queda en FAILED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /*
     * productClient: Bean de Spring generado por Feign en tiempo de ejecución.
     * Detrás de esta interfaz hay un Java Proxy que intercepta cada llamada
     * y la convierte en una petición HTTP a products-service.
     *
     * Si el Circuit Breaker está OPEN, el Proxy invoca el FallbackFactory
     * en lugar de hacer la petición HTTP.
     */
    private final ProductClient productClient;

    // Store en memoria para las órdenes
    private final Map<Long, OrderEntity> orderStore = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    // ──────────────────────────────────────────────────────────────────
    // CREAR ORDEN: el flujo completo con Feign
    // ──────────────────────────────────────────────────────────────────

    /**
     * Crea una nueva orden verificando stock con products-service.
     * <p>
     * Este método demuestra varios escenarios de Feign:
     * 1. Llamada exitosa (200 OK de products-service)
     * 2. Producto no encontrado (404 → ProductNotFoundException vía ErrorDecoder)
     * 3. Stock insuficiente (409 → StockConflictException vía ErrorDecoder)
     * 4. Servicio caído (CB abierto → fallback → orden FAILED)
     */
    
    @Retry(name = "products-service")
    public OrderDTO.Response createOrder(OrderDTO.CreateRequest request) {
        log.info("Creando orden para cliente: {}", request.getCustomerId());

        // ── Fase 1: Verificar disponibilidad de productos ──────────────

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderDTO.OrderItemRequest itemRequest : request.getItems()) {

            /*
             * ── LLAMADA FEIGN #1: GET /products/{id} ──────────────────
             *
             * Aquí Feign:
             *   1. Construye la URL: http://localhost:8081/products/{productId}
             *   2. Envía GET con los headers (incluyendo los del interceptor)
             *   3. Espera respuesta (máx readTimeout=4s)
             *   4. Si 200 → deserializa JSON a ProductDTO.Response
             *   5. Si 404 → ErrorDecoder → ProductNotFoundException
             *   6. Si 503 → ErrorDecoder → RetryableException → Retry → CB fallo → fallback
             *   7. Si CB OPEN → fallback directamente (sin HTTP call)
             *
             * Si el fallback devuelve producto con available=false:
             *   → isAvailableForPurchase() = false → lanzamos ServiceUnavailable
             */
            log.info("Consultando producto ID={} en products-service", itemRequest.getProductId());

            ProductDTO.Response product = productClient.getProductById(itemRequest.getProductId());
            // ↑ Esta línea puede:
            //   a) Devolver el DTO del producto (éxito)
            //   b) Devolver el DTO del fallback (CB abierto)
            //   c) Lanzar ProductNotFoundException (producto no existe)
            //   d) Lanzar StockConflictException (si el ErrorDecoder la mapeó)

            // Verificar si la respuesta viene del fallback (CB estaba abierto)
            log.info("[OrderService] Resultado de ProductClient(Feing)- {}", product.toString());
            if (!product.isAvailableForPurchase()) {
                log.info("Producto {} no disponible (fallback activo)", itemRequest.getProductId());

                log.info("LO QUE LLEGA: {}", product.getException().getClass().getName());

                switch (product.getException().getCause().getClass().getSimpleName()) {
                    case "RetryableException":
                        throw new OrderExceptions.ServiceException(
                                "Producto " + itemRequest.getProductId() + " no disponible temporalmente. " +
                                        "Servicio caído.");
                    case "ProductNotFoundException":
                        throw new OrderExceptions.ProductNotFoundException(
                                "Producto " + itemRequest.getProductId() + " no disponible temporalmente. " +
                                        "Intente mas tarde, el producto no existe."
                        );
                }

            }

            // Verificar stock suficiente
            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                log.info("[OrderService] --- Stock Insuficiente");
                throw new OrderExceptions.StockConflictException(
                        String.format("Stock insuficiente para '%s'. Disponible: %d, Solicitado: %d",
                                product.getName(), product.getStockQuantity(), itemRequest.getQuantity())
                );
            }

            // Calcular subtotal y construir OrderItem
            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            items.add(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())    // snapshot del nombre
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())     // snapshot del precio
                    .build());

            log.debug("Producto '{}' verificado: {} × {} = {}",
                    product.getName(), product.getPrice(), itemRequest.getQuantity(), subtotal);
        }

        // ── Fase 2: Crear la orden en estado PENDING ───────────────────

        long orderId = idSequence.incrementAndGet();
        OrderEntity order = OrderEntity.builder()
                .id(orderId)
                .customerId(request.getCustomerId())
                .userEmail(request.getCustomerEmail())
                .status(EStatus.PENDING)
                .items(items)
                .totalAmount(totalAmount)
                .deliveryAddress(request.getDeliveryAddress())
                .createdAt(LocalDateTime.now())
                .build();

        orderStore.put(orderId, order);
        log.info("Orden #{} creada en estado PENDING. Total: {}", orderId, totalAmount);

        // ── Fase 3: Descontar stock en products-service ────────────────

        try {
            for (OrderItem item : items) {

                /*
                 * ── LLAMADA FEIGN #2: PATCH /products/{id}/stock ─────────
                 *
                 * Enviamos cantidad NEGATIVA para descontar stock.
                 * quantity = -2 → products-service reduce el stock en 2 unidades.
                 *
                 * Si products-service falla aquí:
                 *   - El fallback lanza StockUpdateFailedException
                 *   - La orden queda en FAILED
                 *   - En un sistema real: usarías el patrón SAGA con compensación
                 *     o el patrón Outbox para reintentarlo de forma confiable.
                 */
                log.info("Descontando {} unidades del producto {}", item.getQuantity(), item.getProductId());

                productClient.updateProductStock(
                        item.getProductId(),
                        new ProductClient.StockUpdateRequest(-item.getQuantity()) // ← negativo
                );

                log.debug("Stock descontado: productId={}, cantidad=-{}", item.getProductId(), item.getQuantity());
            }

            // ── Fase 4: Confirmar la orden ─────────────────────────────
            order.setStatus(EStatus.CONFIRMED);
            orderStore.put(orderId, order);

            log.info("Orden #{} CONFIRMADA. Total: {}", orderId, totalAmount);

        } catch (OrderExceptions.StockUpdateFailedException e) {
            /*
             * El fallback de updateProductStock lanzó esta excepción.
             * Esto ocurre cuando:
             *   - El CB de products-service está OPEN
             *   - products-service devuelve 5xx
             * esto en una prueba para ver
             * Marcamos la orden como FAILED.
             * En producción: publicarías un evento Kafka para compensar
             * y reintentar con un worker asíncrono.
             */
            log.error("ESTO ES UN ERROR REAL DE FEIGN", e);
            e.printStackTrace();
            log.error("Fallo al descontar stock. Orden #{} marcada como FAILED: {}", orderId, e.getMessage());
            order.setStatus(EStatus.FAILED);
            orderStore.put(orderId, order);

            // Relanzamos para que el GlobalExceptionHandler devuelva 503
            throw e;
        }

        return OrderDTO.Response.from(order);
    }

    // ──────────────────────────────────────────────────────────────────
    // OTROS MÉTODOS
    // ──────────────────────────────────────────────────────────────────

    public OrderDTO.Response findById(Long orderId) {
        OrderEntity order = orderStore.get(orderId);
        if (order == null) {
            throw new OrderExceptions.OrderNotFoundException(orderId);
        }
        return OrderDTO.Response.from(order);
    }

    public List<OrderDTO.Response> findAll() {
        return orderStore.values().stream()
                .map(OrderDTO.Response::from)
                .toList();
    }

    /**
     * Consulta la disponibilidad del catálogo vía Feign.
     * Útil para probar el Circuit Breaker y el fallback sin crear una orden.
     */
    public List<ProductDTO.Response> getAvailableProducts(String category) {
        log.info("Consultando catálogo de productos: category={}", category);

        /*
         * ── LLAMADA FEIGN: GET /products?category={category} ──────────
         * Si el CB está OPEN → fallback devuelve lista vacía sin llamada HTTP.
         * Si el servicio está UP → devuelve la lista real.
         */
        return productClient.findAllProducts(category);
    }

    /**
     * Verifica el estado de products-service vía Feign.
     * Demuestra cómo el fallback responde cuando el CB está OPEN.
     */
    public String checkProductsServiceHealth() {
        return productClient.checkHealth();
    }
}
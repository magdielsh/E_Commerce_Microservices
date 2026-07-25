package com.e_commerce.orderservice.Controller;

import com.e_commerce.orderservice.DTOs.OrderDTO;
import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Enums.EStatus;
import com.e_commerce.orderservice.Exceptions.GlobalExceptionHandler;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.e_commerce.orderservice.Service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * OrderControllerTest — Tests del controlador REST de órdenes
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * Estrategia:
 *   • @WebMvcTest levanta solo la capa web (no beans de servicio, seguridad, etc.)
 *   • Mockeamos OrderService para enfocarnos SOLO en la serialización JSON,
 *     códigos HTTP, y validación de entrada.
 *   • No levantamos Kafka, JPA, Feign, Eureka → tests aislados y rápidos.
 *   • Registramos manualmente el GlobalExceptionHandler para probar el mapeo
 *     de excepciones a respuestas HTTP.
 */
@WebMvcTest(OrderController.class)
@ContextConfiguration(classes = {OrderController.class, GlobalExceptionHandler.class})
@Import(OrderControllerTest.TestSecurityConfig.class)
@WithMockUser
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /*
     * @MockBean: Spring crea un mock de OrderService y lo inyecta en el contexto.
     * Es el equivalente a @Mock pero en el contexto de Spring.
     * Cualquier bean que dependa de OrderService recibirá este mock.
     */
    @MockBean
    private OrderService orderService;

    // ── Helpers ─────────────────────────────────────────────────────

    private OrderDTO.CreateRequest aValidRequest() {
        OrderDTO.CreateRequest req = new OrderDTO.CreateRequest();
        req.setCustomerId(Long.valueOf(100L));
        req.setCustomerEmail("cliente@test.com");
        req.setItems(List.of(anItem(Long.valueOf(1L), Integer.valueOf(2))));
        req.setDeliveryAddress("Calle Falsa 123");
        return req;
    }

    private OrderDTO.OrderItemRequest anItem(Long productId, Integer quantity) {
        OrderDTO.OrderItemRequest item = new OrderDTO.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private OrderDTO.Response aConfirmedOrder() {
        return OrderDTO.Response.builder()
                .id(Long.valueOf(1L))
                .customerId(Long.valueOf(100L))
                .userEmail("cliente@test.com")
                .status(EStatus.CONFIRMED)
                .items(List.of(
                        OrderDTO.OrderItemResponse.builder()
                                .productId(Long.valueOf(1L))
                                .productName("Laptop")
                                .quantity(Integer.valueOf(1))
                                .unitPrice(new BigDecimal("999.99"))
                                .subtotal(new BigDecimal("1999.98"))
                                .build()
                ))
                .totalAmount(new BigDecimal("1999.98"))
                .deliveryAddress("Calle Falsa 123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // POST /v1/api/orders/createOrder
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /v1/api/orders/createOrder")
    class CreateOrder {

        @Test
        @DisplayName("201 CREATED cuando la orden es válida")
        void returns201() throws Exception {
            // ── given ──
            given(orderService.createOrder(any(OrderDTO.CreateRequest.class)))
                    .willReturn(aConfirmedOrder());

            // ── when & then ──
            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(aValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(Integer.valueOf(1)))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalAmount").value(Double.valueOf(1999.98)));
        }

        @Test
        @DisplayName("400 BAD_REQUEST cuando customerId es null")
        void returns400_whenCustomerIdNull() throws Exception {
            OrderDTO.CreateRequest req = aValidRequest();
            req.setCustomerId(null);

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("400 BAD_REQUEST cuando email es inválido")
        void returns400_whenInvalidEmail() throws Exception {
            OrderDTO.CreateRequest req = aValidRequest();
            req.setCustomerEmail("email-invalido");

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 BAD_REQUEST cuando items está vacío")
        void returns400_whenItemsEmpty() throws Exception {
            OrderDTO.CreateRequest req = aValidRequest();
            req.setItems(List.of());

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("502 BAD_GATEWAY cuando products-service no responde")
        void returns502_whenProductServiceFails() throws Exception {
            // ── given: OrderService lanza ServiceException ──
            given(orderService.createOrder(any(OrderDTO.CreateRequest.class)))
                    .willThrow(new OrderExceptions.ServiceException("Producto no disponible temporalmente"));

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(aValidRequest())))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("UPSTREAM_ERROR"));
        }

        @Test
        @DisplayName("404 NOT_FOUND cuando el producto no existe")
        void returns404_whenProductNotFound() throws Exception {
            given(orderService.createOrder(any(OrderDTO.CreateRequest.class)))
                    .willThrow(new OrderExceptions.ProductNotFoundException(Long.valueOf(999L)));

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(aValidRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("409 CONFLICT cuando hay stock insuficiente")
        void returns409_whenStockConflict() throws Exception {
            given(orderService.createOrder(any(OrderDTO.CreateRequest.class)))
                    .willThrow(new OrderExceptions.StockConflictException(
                            "Stock insuficiente. Disponible: 5, Solicitado: 10"));

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(aValidRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
        }

        @Test
        @DisplayName("503 SERVICE_UNAVAILABLE cuando falla el descuento de stock")
        void returns503_whenStockUpdateFails() throws Exception {
            given(orderService.createOrder(any(OrderDTO.CreateRequest.class)))
                    .willThrow(new OrderExceptions.StockUpdateFailedException(
                            Long.valueOf(1L), "Stock no actualizado"));

            mockMvc.perform(post("/v1/api/orders/createOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(aValidRequest())))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("STOCK_UPDATE_FAILED"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/orders
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /v1/api/orders")
    class GetAllOrders {

        @Test
        @DisplayName("200 OK con lista de órdenes")
        void returns200() throws Exception {
            given(orderService.findAll()).willReturn(List.of(aConfirmedOrder()));

            mockMvc.perform(get("/v1/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("200 OK con lista vacía")
        void returns200_emptyList() throws Exception {
            given(orderService.findAll()).willReturn(List.of());

            mockMvc.perform(get("/v1/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/orders/{id}
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /v1/api/orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("200 OK cuando la orden existe")
        void returns200() throws Exception {
            given(orderService.findById(Long.valueOf(1L))).willReturn(aConfirmedOrder());

            mockMvc.perform(get("/v1/api/orders/{id}", Long.valueOf(1L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(Integer.valueOf(1)));
        }

        @Test
        @DisplayName("404 NOT_FOUND cuando la orden no existe")
        void returns404() throws Exception {
            given(orderService.findById(Long.valueOf(999L)))
                    .willThrow(new OrderExceptions.OrderNotFoundException(Long.valueOf(999L)));

            mockMvc.perform(get("/v1/api/orders/{id}", Long.valueOf(999L)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/orders/products
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /v1/api/orders/products")
    class GetProducts {

        @Test
        @DisplayName("200 OK con lista de productos")
        void returns200() throws Exception {
            given(orderService.getAvailableProducts(null))
                    .willReturn(List.of(ProductDTO.Response.builder()
                            .id(Long.valueOf(1L)).name("Laptop").build()));

            mockMvc.perform(get("/v1/api/orders/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Laptop"));
        }

        @Test
        @DisplayName("200 OK con parámetro category")
        void withCategory() throws Exception {
            given(orderService.getAvailableProducts("ELECTRONICS"))
                    .willReturn(List.of());

            mockMvc.perform(get("/v1/api/orders/products")
                            .param("category", "ELECTRONICS"))
                    .andExpect(status().isOk());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/orders/health
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /v1/api/orders/health")
    class HealthCheck {

        @Test
        @DisplayName("200 OK con estado de servicios")
        void returns200() throws Exception {
            given(orderService.checkProductsServiceHealth())
                    .willReturn("products-service is UP");

            mockMvc.perform(get("/v1/api/orders/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders-service").value("UP"))
                    .andExpect(jsonPath("$.products-service").value("products-service is UP"));
        }
    }

    /**
     * Configuración de seguridad para tests.
     * Deshabilita CSRF y permite todas las requests autenticadas,
     * replicando el comportamiento del SecurityConfig de producción
     * pero sin requerir el InternalRequestFilter.
     */
    @Configuration
    static class TestSecurityConfig {

        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}

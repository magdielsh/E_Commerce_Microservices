package com.e_commerce.orderservice.Service;

import com.e_commerce.orderservice.DTOs.OrderDTO;
import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Enums.EStatus;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.e_commerce.orderservice.Feing.ProductClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import feign.Request;
import feign.RetryableException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * OrderServiceTest — Tests unitarios del servicio de órdenes
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * Estrategia:
 *   • Mockeamos ProductClient con Mockito.
 *   • No levantamos Spring Context (sin @SpringBootTest) → tests ultra rápidos.
 *   • Probamos la lógica de negocio: creación de órdenes, manejo de fallos, 
 *     stock insuficiente, fallback del CB, etc.
 *   • Verificamos que el servicio reacciona correctamente a cada escenario
 *     de Feign (éxito, 404, 409, CB abierto, etc.).
 *
 * Patrón usado: BDDMockito (given/will) porque el lenguaje "given → willReturn" 
 * se lee como: "dado que productClient devuelve X, entonces el servicio hace Y".
 * ───────────────────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    /*
     * @Mock: le decimos a Mockito que cree un proxy de ProductClient.
     * No es el bean real de Feign — es un objeto falso que responde
     * solo cuando configuramos sus métodos con given/willReturn.
     */
    @Mock
    private ProductClient productClient;

    /*
     * La clase bajo test. La instanciamos manualmente con el mock.
     * No usamos @InjectMocks porque queremos control total sobre
     * cómo se construye (es mejor práctica en tests grandes).
     */
    private OrderService orderService;

    /*
     * ─── Datos de prueba reutilizables ───────────────────────────────────
     * Los definimos como constantes al inicio del test para que sean
     * fáciles de cambiar si el dominio evoluciona.
     */
    private static final Long PRODUCT_ID_1 = Long.valueOf(1L);
    private static final Long PRODUCT_ID_2 = Long.valueOf(2L);
    private static final Long NON_EXISTENT_PRODUCT_ID = Long.valueOf(999L);
    private static final String PRODUCT_NAME_1 = "Laptop Pro X1";
    private static final String PRODUCT_NAME_2 = "Mouse Inalámbrico";
    private static final BigDecimal PRICE_1 = new BigDecimal("1299.99");
    private static final BigDecimal PRICE_2 = new BigDecimal("49.99");
    private static final int STOCK_1 = 10;
    private static final int STOCK_2 = 5;
    private static final Long CUSTOMER_ID = Long.valueOf(100L);
    private static final String CUSTOMER_EMAIL = "cliente@test.com";

    /*
     * @BeforeEach: se ejecuta antes de CADA test.
     * Aquí creamos una instancia FRESCA de OrderService para cada test,
     * asegurando que no haya estado compartido entre tests (el Map interno
     * de OrderService arranca vacío cada vez).
     */
    @BeforeEach
    void setUp() {
        orderService = new OrderService(productClient);
    }

    // ════════════════════════════════════════════════════════════════════
    // helper: construye un CreateRequest con productos de ejemplo
    // ════════════════════════════════════════════════════════════════════

    private OrderDTO.CreateRequest aCreateRequest(OrderDTO.OrderItemRequest... items) {
        OrderDTO.CreateRequest req = new OrderDTO.CreateRequest();
        req.setCustomerId(CUSTOMER_ID);
        req.setCustomerEmail(CUSTOMER_EMAIL);
        req.setItems(List.of(items));
        req.setDeliveryAddress("Calle Falsa 123");
        return req;
    }

    private OrderDTO.OrderItemRequest anItem(Long productId, Integer quantity) {
        OrderDTO.OrderItemRequest item = new OrderDTO.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private ProductDTO.Response aProduct(Long id, String name, BigDecimal price, Integer stock) {
        return ProductDTO.Response.builder()
                .id(id)
                .name(name)
                .price(price)
                .stockQuantity(stock)
                .category("ELECTRONICS")
                .active(true)
                .available(Boolean.TRUE)
                .build();
    }

    // ── Helper: crea un RetryableException de Feign ──────────────
    private RetryableException aRetryableException() {
        Request feignRequest = Request.create(
                Request.HttpMethod.GET,
                "http://localhost/v1/api/products/1",
                Collections.emptyMap(),
                (byte[]) null,
                StandardCharsets.UTF_8,
                null
        );
        return new RetryableException(503, "Service unavailable",
                Request.HttpMethod.GET, (Long) null, feignRequest);
    }

    // ════════════════════════════════════════════════════════════════════
    // CREAR ORDEN — ESCENARIOS
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createOrder(): creación de órdenes")
    class CreateOrder {

        /*
         * ── FLUJO FELIZ: createOrder con 2 productos ─────────────────────
         *
         * Dado que:
         *   1. productClient.getProductById(1L) → producto válido con stock
         *   2. productClient.getProductById(2L) → producto válido con stock
         *   3. productClient.updateProductStock(1L, -1) → OK
         *   4. productClient.updateProductStock(2L, -2) → OK
         *
         * Cuando llamamos a orderService.createOrder(...)
         * Entonces:
         *   • La orden se crea con estado CONFIRMED
         *   • El total es PRICE_1×1 + PRICE_2×2 = 1299.99 + 99.98 = 1399.97
         *   • La orden tiene 2 items
         */
        @Test
        @DisplayName("Flujo feliz: crea orden CONFIRMED con 2 productos")
        void happyPath_createsConfirmedOrder() {
            // ── given ──
            ProductDTO.Response product1 = aProduct(PRODUCT_ID_1, PRODUCT_NAME_1, PRICE_1, Integer.valueOf(STOCK_1));
            ProductDTO.Response product2 = aProduct(PRODUCT_ID_2, PRODUCT_NAME_2, PRICE_2, Integer.valueOf(STOCK_2));

            given(productClient.getProductById(PRODUCT_ID_1)).willReturn(product1);
            given(productClient.getProductById(PRODUCT_ID_2)).willReturn(product2);

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(1)),
                    anItem(PRODUCT_ID_2, Integer.valueOf(2))
            );

            // ── when ──
            OrderDTO.Response result = orderService.createOrder(request);

            // ── then ──
            // Verificamos el estado de la orden
            assertThat(result.getStatus()).isEqualTo(EStatus.CONFIRMED);
            assertThat(result.getCustomerId()).isEqualTo(CUSTOMER_ID);

            // Verificamos los items (2 productos)
            assertThat(result.getItems())
                    .hasSize(2)
                    .extracting("productName")
                    .containsExactly(PRODUCT_NAME_1, PRODUCT_NAME_2);

            // Verificamos el total: 1299.99 + 49.99×2 = 1399.97
            assertThat(result.getTotalAmount())
                    .isEqualByComparingTo(new BigDecimal("1399.97"));

            // Verificamos que se llamó a updateProductStock para cada producto
            then(productClient).should(times(1))
                    .updateProductStock(eq(PRODUCT_ID_1), any());
            then(productClient).should(times(1))
                    .updateProductStock(eq(PRODUCT_ID_2), any());
        }

        /*
         * ── PRODUCTO NO ENCONTRADO ─────────────────────────────────────
         *
         * Dado que productClient.getProductById(999L) lanza ProductNotFoundException
         * Cuando llamamos a createOrder con ese producto
         * Entonces el servicio propaga la excepción (no la traga)
         */
        @Test
        @DisplayName("Lanza ProductNotFoundException cuando Feign recibe 404")
        void throws_whenProductNotFound() {
            // ── given ──
            given(productClient.getProductById(NON_EXISTENT_PRODUCT_ID))
                    .willThrow(new OrderExceptions.ProductNotFoundException(NON_EXISTENT_PRODUCT_ID));

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(NON_EXISTENT_PRODUCT_ID, Integer.valueOf(1))
            );

            // ── when & then ──
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderExceptions.ProductNotFoundException.class)
                    .hasMessageContaining(String.valueOf(NON_EXISTENT_PRODUCT_ID));
        }

        /*
         * ── STOCK INSUFICIENTE ─────────────────────────────────────────
         *
         * Dado que product1 tiene stock=5 y pedimos quantity=10
         * Cuando llamamos a createOrder
         * Entonces lanza StockConflictException
         */
        @Test
        @DisplayName("Lanza StockConflictException cuando el stock es insuficiente")
        void throws_whenStockInsufficient() {
            // ── given: producto con stock=5, pedimos 10
            ProductDTO.Response product = aProduct(PRODUCT_ID_1, PRODUCT_NAME_1, PRICE_1, Integer.valueOf(5));
            given(productClient.getProductById(PRODUCT_ID_1)).willReturn(product);

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(10))  // pedimos 10, hay 5
            );

            // ── when & then ──
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderExceptions.StockConflictException.class)
                    .hasMessageContaining("insuficiente")
                    .hasMessageContaining("5");  // stock disponible
        }

        /*
         * ── FALLBACK ACTIVO (CB abierto) → producto con available=false ──
         *
         * Dado que productClient devuelve un producto con available=false
         * (lo que hace el fallbackFactory cuando el CB está abierto)
         * Cuando createOrder lo recibe
         * Entonces lanza ServiceException porque el producto no está disponible
         *
         * Nota: el fallback también setea el campo 'exception' con la causa real.
         * Para simplificar, testeamos que available=false → lanza excepción.
         */
        @Test
        @DisplayName("Lanza ServiceException cuando el fallback devuelve producto no disponible")
        void throws_whenFallbackProductNotAvailable() {
            // ── given: producto del fallback (available=false, active=false)
            ProductDTO.Response fallbackProduct = ProductDTO.Response.builder()
                    .id(PRODUCT_ID_1)
                    .name("Producto temporalmente no disponible")
                    .price(BigDecimal.ZERO)
                    .stockQuantity(Integer.valueOf(0))
                    .active(false)
                    .available(Boolean.FALSE)
                    .exception(new RuntimeException(aRetryableException()))
                    .build();

            given(productClient.getProductById(PRODUCT_ID_1))
                    .willReturn(fallbackProduct);

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(1))
            );

            // ── when & then ──
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderExceptions.ServiceException.class)
                    .hasMessageContaining("no disponible");
        }

        /*
         * ── FALLBACK CON PRODUCTO 404 ──────────────────────────────────
         *
         * El fallback puede setear exception con ProductNotFoundException.
         * El código actual hace un switch por el nombre de la excepción.
         */
        @Test
        @DisplayName("Lanza ProductNotFoundException cuando fallback indica producto no existente")
        void throws_whenFallbackProductNotFound() {
            // ── given: fallback con ProductNotFoundException
            ProductDTO.Response fallbackProduct = ProductDTO.Response.builder()
                    .id(PRODUCT_ID_1)
                    .name("Producto temporalmente no disponible")
                    .price(BigDecimal.ZERO)
                    .stockQuantity(Integer.valueOf(0))
                    .active(false)
                    .available(Boolean.FALSE)
                    .exception(new RuntimeException(
                            new OrderExceptions.ProductNotFoundException(PRODUCT_ID_1)))
                    .build();

            given(productClient.getProductById(PRODUCT_ID_1))
                    .willReturn(fallbackProduct);

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(1))
            );

            // ── when & then ──
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderExceptions.ProductNotFoundException.class);
        }

        /*
         * ── ERROR EN updateProductStock (StockUpdateFailedException) ───
         *
         * Dado que:
         *   1. getProductById funciona OK
         *   2. updateProductStock lanza StockUpdateFailedException
         *
         * Cuando createOrder falla al descontar stock
         * Entonces la orden queda en FAILED internamente y se propaga la excepción
         */
        @Test
        @DisplayName("Orden queda FAILED cuando falla el descuento de stock")
        void orderFailed_whenStockUpdateFails() {
            // ── given: producto válido, pero updateProductStock falla ──
            ProductDTO.Response product = aProduct(PRODUCT_ID_1, PRODUCT_NAME_1, PRICE_1, Integer.valueOf(STOCK_1));
            given(productClient.getProductById(PRODUCT_ID_1)).willReturn(product);
            given(productClient.updateProductStock(Long.valueOf(anyLong()), any()))
                    .willThrow(new OrderExceptions.StockUpdateFailedException(
                            PRODUCT_ID_1, "Stock no actualizado: CB abierto"));

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(2))
            );

            // ── when & then ──
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(OrderExceptions.StockUpdateFailedException.class)
                    .hasMessageContaining("Stock no actualizado");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONSULTAR ÓRDENES
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById() / findAll(): consulta de órdenes")
    class FindOrders {

        /*
         * Para estos tests necesitamos que orderService tenga al menos
         * una orden en su store. La única forma es crear una orden exitosa
         * primero (usando el happy path).
         */
        private OrderDTO.Response createSampleOrder() {
            ProductDTO.Response product = aProduct(PRODUCT_ID_1, PRODUCT_NAME_1, PRICE_1, Integer.valueOf(STOCK_1));
            given(productClient.getProductById(PRODUCT_ID_1)).willReturn(product);

            OrderDTO.CreateRequest request = aCreateRequest(
                    anItem(PRODUCT_ID_1, Integer.valueOf(1))
            );

            return orderService.createOrder(request);
        }

        @Test
        @DisplayName("findById devuelve la orden cuando existe")
        void findById_returnsOrder() {
            // ── given: creamos una orden primero ──
            OrderDTO.Response created = createSampleOrder();

            // ── when: la buscamos por ID ──
            OrderDTO.Response found = orderService.findById(created.getId());

            // ── then ──
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(found.getStatus()).isEqualTo(EStatus.CONFIRMED);
        }

        @Test
        @DisplayName("findById lanza OrderNotFoundException cuando no existe")
        void findById_throwsWhenNotFound() {
            assertThatThrownBy(() -> orderService.findById(Long.valueOf(999L)))
                    .isInstanceOf(OrderExceptions.OrderNotFoundException.class);
        }

        @Test
        @DisplayName("findAll devuelve todas las órdenes")
        void findAll_returnsAllOrders() {
            // ── given: creamos una orden ──
            createSampleOrder();

            // ── when ──
            List<OrderDTO.Response> all = orderService.findAll();

            // ── then ──
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getCustomerId()).isEqualTo(CUSTOMER_ID);
        }

        @Test
        @DisplayName("findAll devuelve lista vacía cuando no hay órdenes")
        void findAll_empty() {
            assertThat(orderService.findAll()).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LLAMADAS FEIGN (CB)
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAvailableProducts() / checkProductsServiceHealth(): llamadas Feign")
    class FeignCalls {

        /*
         * Estos tests verifican que OrderService delega correctamente
         * en ProductClient sin transformar los datos. No probamos
         * el CB aquí (eso es responsabilidad de resilience4j).
         */
        @Test
        @DisplayName("getAvailableProducts devuelve la lista del FeignClient")
        void delegatesToFeign() {
            // ── given ──
            List<ProductDTO.Response> products = List.of(
                    aProduct(PRODUCT_ID_1, PRODUCT_NAME_1, PRICE_1, Integer.valueOf(STOCK_1)),
                    aProduct(PRODUCT_ID_2, PRODUCT_NAME_2, PRICE_2, Integer.valueOf(STOCK_2))
            );
            given(productClient.findAllProducts(null)).willReturn(products);

            // ── when ──
            List<ProductDTO.Response> result = orderService.getAvailableProducts(null);

            // ── then ──
            assertThat(result).hasSize(2);
            then(productClient).should(times(1)).findAllProducts(null);
        }

        @Test
        @DisplayName("getAvailableProducts pasa el filtro de categoría")
        void passesCategoryFilter() {
            // ── given ──
            given(productClient.findAllProducts("ELECTRONICS")).willReturn(List.of());

            // ── when ──
            orderService.getAvailableProducts("ELECTRONICS");

            // ── then ──
            then(productClient).should(times(1)).findAllProducts("ELECTRONICS");
        }

        @Test
        @DisplayName("checkProductsServiceHealth delega en el FeignClient")
        void healthCheck_delegates() {
            // ── given ──
            given(productClient.checkHealth()).willReturn("products-service is UP");

            // ── when ──
            String health = orderService.checkProductsServiceHealth();

            // ── then ──
            assertThat(health).contains("UP");
            then(productClient).should(times(1)).checkHealth();
        }

        @Test
        @DisplayName("checkProductsServiceHealth refleja CB abierto")
        void healthCheck_cbOpen() {
            // ── given: fallback devuelve "DOWN" ──
            given(productClient.checkHealth())
                    .willReturn("products-service DOWN (fallback activo - Circuit Breaker: CallNotPermittedException)");

            // ── when ──
            String health = orderService.checkProductsServiceHealth();

            // ── then ──
            assertThat(health).contains("DOWN");
        }
    }
}

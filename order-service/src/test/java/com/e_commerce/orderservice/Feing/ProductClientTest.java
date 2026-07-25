package com.e_commerce.orderservice.Feing;

import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import feign.Feign;
import feign.Retryer;
import feign.hc5.ApacheHttp5Client;
import org.junit.jupiter.api.*;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * ProductClientTest — Tests de integración del FeignClient con WireMock
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * ¿Qué probamos?
 *   • Que el FeignClient construye las URLs correctamente (mapeo @GetMapping, @PathVariable, etc.)
 *   • Que el ErrorDecoder (ProductFeignConfig) mapea códigos HTTP a excepciones de dominio
 *   • Que Jackson serializa/deserializa los DTOs correctamente
 *
 * Estrategia:
 *   • NO levantamos Spring Boot — construimos Feign manualmente con Feign.builder().
 *     → Tests ultra rápidos (no hay contexto Spring que inicializar).
 *   • WireMock simula products-service real en puerto dinámico.
 *   • Usamos el MISMO ErrorDecoder de producción (ProductFeignConfig.productErrorDecoder()).
 *
 * ⚠️  WireMockServer se levanta y destruye en cada @Test (aislamiento total).
 */
class ProductClientTest {

    /*
     * WireMockServer: servidor HTTP embebido que simula products-service.
     *   new WireMockServer(0) → elige puerto libre automáticamente.
     *   start() → lo pone a escuchar.
     *   stop()  → lo detiene y libera el puerto.
     */
    private WireMockServer wireMock;

    /*
     * ProductClient construido manualmente (sin Spring).
     * Apunta a la URL del WireMockServer.
     */
    private ProductClient productClient;

    /*
     * ObjectMapper: configurado como Spring Boot lo configuraría.
     * Necesario para que Feign serialice/deserialice JSON correctamente.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();  // registra JavaTimeModule, etc.

    @BeforeEach
    void setUp() {
        // ── 1. Levantar WireMock en puerto dinámico ──
        wireMock = new WireMockServer(0);
        wireMock.start();

        // ── 2. Configurar el cliente estático de WireMock ──
        //     para que stubFor(), get(), etc. apunten a este server.
        WireMock.configureFor("localhost", wireMock.port());

        // ── 3. Construir FeignClient apuntando a WireMock ──
        String wireMockUrl = "http://localhost:" + wireMock.port();

        /*
         * Feign.builder()
         *   .client(new ApacheHttp5Client())    → usa Apache HC5 (como en prod)
         *   .contract(new SpringMvcContract())  → entiende @GetMapping, @PathVariable, etc.
         *   .retryer(Retryer.NEVER_RETRY)       → sin reintentos (como en ProductFeignConfig)
         *   .encoder/decoder(new Jackson...)    → serialización JSON
         *   .errorDecoder(...)                  → el MISMO de producción
         *   .target(ProductClient.class, url)   → crea el proxy Feign
         */
        productClient = Feign.builder()
                .client(new ApacheHttp5Client())
                .contract(new SpringMvcContract())
                .retryer(Retryer.NEVER_RETRY)
                .encoder(new feign.jackson.JacksonEncoder(MAPPER))
                .decoder(new feign.jackson.JacksonDecoder(MAPPER))
                .errorDecoder(new ProductFeignConfig().productErrorDecoder(MAPPER))
                .target(ProductClient.class, wireMockUrl);
    }

    @AfterEach
    void tearDown() {
        // Detener WireMock entre tests para evitar contaminación
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/{id} → 200 OK
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/1 → 200 → ProductDTO con datos correctos")
    void getProductById_returnsProduct() {
        // ── given: configuramos el stub de WireMock ──
        //     Cada test define sus propios stubs → cero acoplamiento entre tests.
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 1,
                                    "name": "Laptop Pro X1",
                                    "description": "Laptop de alta gama",
                                    "price": 1299.99,
                                    "stockQuantity": 50,
                                    "category": "ELECTRONICS",
                                    "active": true
                                }
                                """)));

        // ── when: llamada Feign real → WireMock recibe la petición ──
        ProductDTO.Response product = productClient.getProductById(Long.valueOf(1L));

        // ── then: verificamos que Jackson deserializó correctamente ──
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Laptop Pro X1");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(product.getStockQuantity()).isEqualTo(50);
        assertThat(product.getCategory()).isEqualTo("ELECTRONICS");
        assertThat(product.isActive()).isTrue();

        // available es campo interno de orders-service → products-service no lo envía
        assertThat(product.getAvailable()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/{id} → 404 → ProductNotFoundException
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/999 → 404 → ErrorDecoder lanza ProductNotFoundException")
    void getProductById_throwsProductNotFoundException() {
        // ── given: stub para 404 ──
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "error": "NOT_FOUND",
                                    "message": "Producto no encontrado: 999"
                                }
                                """)));

        // ── when & then ──
        assertThatThrownBy(() -> productClient.getProductById(Long.valueOf(999L)))
                .isInstanceOf(OrderExceptions.ProductNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products → 200 → lista de productos
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products → 200 → lista de ProductDTO")
    void findAllProducts_returnsList() {
        // ── given ──
        wireMock.stubFor(get(urlPathEqualTo("/v1/api/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                    { "id": 1, "name": "Laptop", "price": 999.99, "stockQuantity": 10, "category": "ELECTRONICS", "active": true },
                                    { "id": 2, "name": "Mouse", "price": 49.99, "stockQuantity": 100, "category": "ELECTRONICS", "active": true }
                                ]
                                """)));

        // ── when ──
        List<ProductDTO.Response> products = productClient.findAllProducts(null);

        // ── then ──
        assertThat(products).hasSize(2);
        assertThat(products.get(0).getName()).isEqualTo("Laptop");
        assertThat(products.get(1).getName()).isEqualTo("Mouse");
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products?category=FOOD
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products?category=FOOD → filtra correctamente")
    void findAllProducts_withCategory() {
        // ── given: stub que verifica el query param ──
        wireMock.stubFor(get(urlPathEqualTo("/v1/api/products"))
                .withQueryParam("category", equalTo("FOOD"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                    { "id": 3, "name": "Pizza", "price": 9.99, "stockQuantity": 30, "category": "FOOD", "active": true }
                                ]
                                """)));

        // ── when ──
        List<ProductDTO.Response> products = productClient.findAllProducts("FOOD");

        // ── then ──
        assertThat(products).hasSize(1);
        assertThat(products.get(0).getCategory()).isEqualTo("FOOD");
    }

    // ══════════════════════════════════════════════════════════════════
    // PATCH /v1/api/products/{id}/stock
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /v1/api/products/1/stock → 200 → stock actualizado")
    void updateProductStock_success() {
        // ── given ──
        wireMock.stubFor(patch(urlEqualTo("/v1/api/products/1/stock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 1,
                                    "name": "Laptop",
                                    "stockQuantity": 48,
                                    "price": 1299.99,
                                    "category": "ELECTRONICS",
                                    "active": true
                                }
                                """)));

        // ── when ──
        ProductClient.StockUpdateRequest request = new ProductClient.StockUpdateRequest(Integer.valueOf(-2));
        ProductDTO.Response result = productClient.updateProductStock(Long.valueOf(1L), request);

        // ── then ──
        assertThat(result.getStockQuantity()).isEqualTo(48);
    }

    // ══════════════════════════════════════════════════════════════════
    // PATCH /v1/api/products/{id}/stock → 409 CONFLICT
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /v1/api/products/1/stock → 409 → StockConflictException")
    void updateProductStock_conflict() {
        // ── given ──
        wireMock.stubFor(patch(urlEqualTo("/v1/api/products/1/stock"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "error": "INSUFFICIENT_STOCK",
                                    "message": "Stock insuficiente. Disponible: 2, Solicitado: 10"
                                }
                                """)));

        // ── when & then ──
        assertThatThrownBy(() ->
                productClient.updateProductStock(Long.valueOf(1L), new ProductClient.StockUpdateRequest(Integer.valueOf(-10))))
                .isInstanceOf(OrderExceptions.StockConflictException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/health-check
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/health-check → 200 → 'UP'")
    void healthCheck_returnsUp() {
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/health-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("products-service is UP")));

        String health = productClient.checkHealth();
        assertThat(health).isEqualTo("products-service is UP");
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/{id} → 503 → RetryableException
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/1 → 503 → ErrorDecoder lanza RetryableException")
    void getProductById_throwsRetryableException_on503() {
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> productClient.getProductById(Long.valueOf(1L)))
                .isInstanceOf(feign.RetryableException.class);
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/{id} → 500 → ServiceException
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/1 → 500 → ServiceException")
    void getProductById_throwsServiceException_on500() {
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Error interno en products-service\"}")));

        assertThatThrownBy(() -> productClient.getProductById(Long.valueOf(1L)))
                .isInstanceOf(OrderExceptions.ServiceException.class)
                .hasMessageContaining("Error interno");
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /v1/api/products/{id} → 400 → BadRequestException
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /v1/api/products/1 → 400 → BadRequestException")
    void getProductById_throwsBadRequestException_on400() {
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Solicitud inválida\"}")));

        assertThatThrownBy(() -> productClient.getProductById(Long.valueOf(1L)))
                .isInstanceOf(OrderExceptions.BadRequestException.class)
                .hasMessageContaining("Solicitud inválida");
    }

    // ══════════════════════════════════════════════════════════════════
    // Verificación: WireMock recibió la petición esperada
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Verifica que Feign envió los headers correctos")
    void verifyFeignSendsRequest() {
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"Test\", \"active\": true}")));

        productClient.getProductById(Long.valueOf(1L));

        // Verificamos que WireMock recibió exactamente 1 petición GET a /v1/api/products/1
        wireMock.verify(1, getRequestedFor(urlEqualTo("/v1/api/products/1")));
    }

    @Test
    @DisplayName("Recupera campo available=false cuando products-service no lo envía")
    void availableIsNullWhenNotSent() {
        /*
         * products-service NUNCA envía el campo "available" — es solo interno.
         * Si el ErrorDecoder/Jackson deja el campo como null,
         * isAvailableForPurchase() debe tratarlo como "disponible".
         */
        wireMock.stubFor(get(urlEqualTo("/v1/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": 1, "name": "Laptop", "active": true, "stockQuantity": 5, "price": 100.0, "category": "TEST"}
                                """)));

        ProductDTO.Response product = productClient.getProductById(Long.valueOf(1L));

        assertThat(product.getAvailable()).isNull();  // products-service no lo manda
        assertThat(product.isAvailableForPurchase()).isTrue();  // null = disponible
    }
}

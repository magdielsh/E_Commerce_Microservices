package com.e_commerce.orderservice.Feing;

import com.e_commerce.orderservice.Exceptions.OrderExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * ProductFeignConfigTest — Tests unitarios del ErrorDecoder
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * ¿Qué probamos?
 *   • El ErrorDecoder de ProductFeignConfig debe transformar códigos HTTP
 *     en las excepciones de dominio correctas.
 *
 * ¿Por qué tests separados de ProductClientTest?
 *   • ProductClientTest prueba la integración COMPLETA (Feign + ErrorDecoder + Jackson).
 *   • Este test prueba SOLO el ErrorDecoder, de forma aislada y ultra rápida.
 *   • Separación de concerns: un test de integración + un test unitario.
 *
 * Construimos manualmente objetos feign.Response sin llamar HTTP real.
 */
class ProductFeignConfigTest {

    private ErrorDecoder errorDecoder;
    private ObjectMapper objectMapper;

    /*
     * Método helper: construye una respuesta Feign falsa.
     *
     * Parámetros:
     *   status  → código HTTP (404, 409, 503, etc.)
     *   body    → cuerpo JSON que el ErrorDecoder leerá
     *
     * feign.Request y feign.Response tienen constructores estáticos
     * que permiten crear respuestas sin servidor real.
     */
    private Response createResponse(int status, String body) {
        // Request HTTP falso (no se envía, solo para construir la Response)
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost/v1/api/products/1",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        // Cuerpo de la respuesta
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];

        return Response.builder()
                .status(status)
                .reason("Mock reason")
                .headers(Collections.emptyMap())
                .body(bodyBytes)
                .request(request)
                .build();
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        errorDecoder = new ProductFeignConfig().productErrorDecoder(objectMapper);
    }

    @Test
    @DisplayName("404 → ProductNotFoundException")
    void decode404_returnsProductNotFound() {
        Response response = createResponse(404, """
                {"error": "NOT_FOUND", "message": "Producto no encontrado: 1"}
                """);

        Exception result = errorDecoder.decode("ProductClient#getProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.ProductNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");
    }

    @Test
    @DisplayName("409 → StockConflictException")
    void decode409_returnsStockConflict() {
        Response response = createResponse(409, """
                {"error": "INSUFFICIENT_STOCK", "message": "Stock insuficiente. Disponible: 2, Solicitado: 10"}
                """);

        Exception result = errorDecoder.decode("ProductClient#updateProductStock(Long,StockUpdateRequest)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.StockConflictException.class)
                .hasMessageContaining("insuficiente");
    }

    @Test
    @DisplayName("400 → BadRequestException")
    void decode400_returnsBadRequest() {
        Response response = createResponse(400, """
                {"message": "Solicitud inválida"}
                """);

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.BadRequestException.class)
                .hasMessageContaining("Solicitud inválida");
    }

    @Test
    @DisplayName("503 → RetryableException")
    void decode503_returnsRetryable() {
        Response response = createResponse(503, "Service Unavailable");

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("502 → RetryableException")
    void decode502_returnsRetryable() {
        Response response = createResponse(502, "Bad Gateway");

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("504 → RetryableException")
    void decode504_returnsRetryable() {
        Response response = createResponse(504, "Gateway Timeout");

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("500 → ServiceException")
    void decode500_returnsServiceException() {
        Response response = createResponse(500, """
                {"message": "Error interno en products-service"}
                """);

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.ServiceException.class)
                .hasMessageContaining("Error interno");
    }

    @Test
    @DisplayName("200 → excepción genérica (no debería ocurrir, pero por si acaso)")
    void decode200_returnsDefaultMessage() {
        Response response = createResponse(200, "OK");

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        // El ErrorDecoder solo se ejecuta para códigos 4xx/5xx,
        // pero si llega un 200, cae en default → ServiceException
        assertThat(result)
                .isInstanceOf(OrderExceptions.ServiceException.class)
                .hasMessageContaining("Error inesperado");
    }

    @Test
    @DisplayName("Body vacío → no lanza NullPointerException")
    void emptyBody_doesNotThrowNpe() {
        Response response = createResponse(500, null);

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.ServiceException.class);
    }

    @Test
    @DisplayName("Body no JSON → no lanza excepción de parseo")
    void nonJsonBody_doesNotThrow() {
        Response response = createResponse(404, "Not Found Plain Text");

        Exception result = errorDecoder.decode("ProductClient#findProductById(Long)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.ProductNotFoundException.class);
    }

    @Test
    @DisplayName("ErrorDecoder identifica el método por methodKey")
    void methodKeyInLog() {
        // Diferentes methodKeys deberían funcionar con el mismo decoder
        Response response = createResponse(404, """
                {"error": "NOT_FOUND", "message": "No encontrado"}
                """);

        Exception result = errorDecoder.decode("ProductClient#findAllProducts(String)", response);

        assertThat(result)
                .isInstanceOf(OrderExceptions.ProductNotFoundException.class);
    }
}

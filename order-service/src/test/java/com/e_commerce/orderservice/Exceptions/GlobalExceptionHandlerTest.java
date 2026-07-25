package com.e_commerce.orderservice.Exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * GlobalExceptionHandlerTest — Tests del manejador global de excepciones
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * Verificamos que cada excepción de dominio se mapea al código HTTP y
 * estructura JSON correctos.
 *
 * MOCK: HttpServletRequest → solo necesitamos getRequestURI() para el path.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        lenient().when(request.getRequestURI()).thenReturn("/v1/api/orders/1");
    }

    @Test
    @DisplayName("ProductNotFoundException → 404 NOT_FOUND con error=PRODUCT_NOT_FOUND")
    void handleProductNotFound() {
        var ex = new OrderExceptions.ProductNotFoundException("Producto no encontrado: 999");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleProductNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getPath()).isEqualTo("/v1/api/orders/1");
    }

    @Test
    @DisplayName("OrderNotFoundException → 404 NOT_FOUND con error=ORDER_NOT_FOUND")
    void handleOrderNotFound() {
        var ex = new OrderExceptions.OrderNotFoundException(Long.valueOf(1L));

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleOrderNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("StockConflictException → 409 CONFLICT con error=INSUFFICIENT_STOCK")
    void handleStockConflict() {
        var ex = new OrderExceptions.StockConflictException("Stock insuficiente");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleStockConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("StockUpdateFailedException → 503 SERVICE_UNAVAILABLE con error=STOCK_UPDATE_FAILED")
    void handleStockUpdateFailed() {
        var ex = new OrderExceptions.StockUpdateFailedException(Long.valueOf(1L), "No se pudo actualizar stock");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleStockUpdateFailed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError()).isEqualTo("STOCK_UPDATE_FAILED");
        // Verifica que el mensaje incluye el productId
        assertThat(response.getBody().getMessage()).contains("productId=1");
    }

    @Test
    @DisplayName("ServiceException → 502 BAD_GATEWAY con error=UPSTREAM_ERROR")
    void handleServiceError() {
        var ex = new OrderExceptions.ServiceException("Error en products-service");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleServiceError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError()).isEqualTo("UPSTREAM_ERROR");
        assertThat(response.getBody().getStatus()).isEqualTo(502);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 BAD_REQUEST con errores de validación")
    void handleValidation() {
        /*
         * Construimos MethodArgumentNotValidException con BindingResult mockeado.
         * En un test real de controller (@WebMvcTest) esto se prueba mejor
         * con MockMvc y peticiones inválidas. Aquí probamos la lógica del handler.
         */
        BindingResult bindingResult = mock(BindingResult.class);
        given(bindingResult.getFieldErrors()).willReturn(List.of(
                new FieldError("createRequest", "customerId", "El ID del cliente es obligatorio"),
                new FieldError("createRequest", "items", "Debe tener al menos un producto")
        ));

        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getValidationErrors())
                .hasSize(2)
                .contains("El ID del cliente es obligatorio");
    }

    @Test
    @DisplayName("Exception genérica → 500 INTERNAL_SERVER_ERROR")
    void handleGeneral() {
        var ex = new RuntimeException("Error inesperado");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleGeneral(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("ErrorResponse incluye timestamp")
    void errorResponseHasTimestamp() {
        var ex = new OrderExceptions.ProductNotFoundException("test");

        ResponseEntity<OrderExceptions.ErrorResponse> response =
                handler.handleProductNotFound(ex, request);

        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("ErrorResponse.withValidationErrors retorna el mismo objeto")
    void withValidationErrors_returnsSelf() {
        OrderExceptions.ErrorResponse error = new OrderExceptions.ErrorResponse(
                400, "VALIDATION_ERROR", "test", "/path");

        OrderExceptions.ErrorResponse result = error.withValidationErrors(List.of("error1"));

        assertThat(result).isSameAs(error);
        assertThat(result.getValidationErrors()).containsExactly("error1");
    }
}

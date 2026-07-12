package com.e_commerce.productservice.Exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Excepciones de dominio del servicio de Productos.
 * <p>
 * Cada excepción está anotada con @ResponseStatus para que Spring
 * la convierta automáticamente al código HTTP correcto.
 * <p>
 * Cuando Feign recibe un 404, su ErrorDecoder (en orders-service)
 * lo transforma en ProductNotFoundException del lado del consumidor.
 */

public class ProductExceptions {

    /**
     * ProductNotFoundException → HTTP 404 NOT FOUND
     * <p>
     * Se lanza cuando se busca un producto por ID y no existe.
     * El GlobalExceptionHandler la captura y devuelve un ErrorResponse.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ProductNotFoundException extends RuntimeException {
        private final Long productId;

        public ProductNotFoundException(Long productId) {
            super("Producto no encontrado con ID: " + productId);
            this.productId = productId;
        }

        public Long getProductId() {
            return productId;
        }
    }

    /**
     * InsufficientStockException → HTTP 409 CONFLICT
     * <p>
     * Se lanza cuando se intenta reducir el stock por debajo de 0.
     * 409 Conflict: el estado actual del recurso impide la operación.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(Long productId, int available, int requested) {
            super(String.format(
                    "Stock insuficiente para producto %d. Disponible: %d, Solicitado: %d",
                    productId, available, requested
            ));
        }
    }

    /**
     * ProductAlreadyExistsException → HTTP 409 CONFLICT
     * <p>
     * Se lanza al intentar crear un producto con nombre duplicado.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ProductAlreadyExistsException extends RuntimeException {
        public ProductAlreadyExistsException(String name) {
            super("Ya existe un producto con el nombre: " + name);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DTO de error: estructura estándar de respuestas de error.
    // Todos los errores de la API tienen este formato JSON:
    // {
    //   "timestamp": "2024-01-15T10:30:00",
    //   "status": 404,
    //   "error": "NOT_FOUND",
    //   "message": "Producto no encontrado con ID: 99",
    //   "path": "/products/99"
    // }
    // ─────────────────────────────────────────────────────────────
    @Getter
    public static class ErrorResponse {
        private final LocalDateTime timestamp;
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private List<String> validationErrors; // solo en errores 400

        public ErrorResponse(int status, String error, String message, String path) {
            this.timestamp = LocalDateTime.now();
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
        }

        public ErrorResponse withValidationErrors(List<String> errors) {
            this.validationErrors = errors;
            return this;
        }
    }
}

package com.e_commerce.orderservice.Exceptions;


import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Excepciones de dominio del servicio de Órdenes.
 *
 * Separamos las excepciones de negocio de las excepciones de infraestructura:
 *
 *   Negocio (predecibles):
 *     - ProductNotFoundException: el producto no existe en el catálogo
 *     - StockConflictException: stock insuficiente
 *     - OrderNotFoundException: la orden no existe
 *
 *   Infraestructura (problemas técnicos):
 *     - ServiceException: error en products-service
 *     - StockUpdateFailedException: no se pudo descontar stock (CB abierto)
 */
public class OrderExceptions {

    /**
     * Se lanza cuando Feign recibe un 404 de products-service.
     * El ErrorDecoder en ProductFeignConfig la instancia.
     * El GlobalExceptionHandler la convierte en 404 para el cliente de orders-service.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
        public ProductNotFoundException(Long productId) {
            super("Producto no encontrado: " + productId);
        }
    }

    /**
     * Orden no encontrada en el store de orders-service.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(Long orderId) {
            super("Orden no encontrada: " + orderId);
        }
    }

    /**
     * Stock insuficiente (409 de products-service, mapeado por ErrorDecoder).
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class StockConflictException extends RuntimeException {
        public StockConflictException(String message) {
            super(message);
        }
    }

    /**
     * No se pudo actualizar el stock porque products-service no estaba disponible.
     * Puede requerir compensación (SAGA): cancelar la orden o poner en cola de reintentos.
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class StockUpdateFailedException extends RuntimeException {
        @Getter
        private final Long productId;

        public StockUpdateFailedException(Long productId, String message) {
            super(message);
            this.productId = productId;
        }
    }

    /**
     * Error genérico de products-service (5xx). El CB lo cuenta como fallo.
     */
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public static class ServiceException extends RuntimeException {
        public ServiceException(String message) {
            super(message);
        }
    }

    /**
     * Solicitud inválida enviada a products-service (error nuestro, 400).
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    // ── ErrorResponse: mismo formato que products-service para consistencia ──
    @Getter
    public static class ErrorResponse {
        private final LocalDateTime timestamp = LocalDateTime.now();
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private List<String> validationErrors;

        public ErrorResponse(int status, String error, String message, String path) {
            this.status  = status;
            this.error   = error;
            this.message = message;
            this.path    = path;
        }

        public ErrorResponse withValidationErrors(List<String> errors) {
            this.validationErrors = errors;
            return this;
        }
    }
}
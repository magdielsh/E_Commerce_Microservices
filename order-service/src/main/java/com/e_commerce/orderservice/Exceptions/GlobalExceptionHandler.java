package com.e_commerce.orderservice.Exceptions;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Manejador global de excepciones para orders-service.
 *
 * Incluye manejadores para:
 *   - Excepciones de dominio propias (OrderNotFound, StockConflict...)
 *   - Excepciones que vienen de products-service via Feign (ProductNotFound...)
 *   - Errores de validación de los DTOs de entrada
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderExceptions.ProductNotFoundException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleProductNotFound(
            OrderExceptions.ProductNotFoundException ex, HttpServletRequest req) {

        log.info("[GlobalExceptionHandler - OrderService] - Producto no encontrado al procesar orden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new OrderExceptions.ErrorResponse(404, "PRODUCT_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(OrderExceptions.OrderNotFoundException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleOrderNotFound(
            OrderExceptions.OrderNotFoundException ex, HttpServletRequest req) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new OrderExceptions.ErrorResponse(404, "ORDER_NOT_FOUND",
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(OrderExceptions.StockConflictException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleStockConflict(
            OrderExceptions.StockConflictException ex, HttpServletRequest req) {

        log.warn("Conflicto de stock: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new OrderExceptions.ErrorResponse(409, "INSUFFICIENT_STOCK",
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(OrderExceptions.StockUpdateFailedException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleStockUpdateFailed(
            OrderExceptions.StockUpdateFailedException ex, HttpServletRequest req) {

        log.error("Stock no actualizado para productId={}: {}", ex.getProductId(), ex.getMessage());
        // 503: el servicio dependiente no está disponible → el cliente puede reintentar
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new OrderExceptions.ErrorResponse(503, "STOCK_UPDATE_FAILED",
                        ex.getMessage() + " (productId=" + ex.getProductId() + ")",
                        req.getRequestURI()));
    }

    @ExceptionHandler(OrderExceptions.ServiceException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleServiceError(
            OrderExceptions.ServiceException ex, HttpServletRequest req) {

        log.error("Error en servicio externo: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new OrderExceptions.ErrorResponse(502, "UPSTREAM_ERROR",
                        "Error en servicio externo: " + ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream().map(FieldError::getDefaultMessage).toList();

        return ResponseEntity.badRequest()
                .body(new OrderExceptions.ErrorResponse(400, "VALIDATION_ERROR",
                        "Datos inválidos", req.getRequestURI())
                        .withValidationErrors(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OrderExceptions.ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {

        log.error("Error inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(new OrderExceptions.ErrorResponse(500, "INTERNAL_ERROR",
                        "Error interno del servidor", req.getRequestURI()));
    }
}
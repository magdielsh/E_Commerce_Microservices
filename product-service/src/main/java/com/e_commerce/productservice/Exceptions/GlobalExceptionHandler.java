package com.e_commerce.productservice.Exceptions;

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
 * Manejador global de excepciones.
 *
 * @RestControllerAdvice: intercepta excepciones lanzadas desde cualquier
 * @RestController del servicio y las convierte en respuestas HTTP estructuradas.
 * <p>
 * Sin esto, Spring devolvería un HTML de error genérico (el "Whitelabel Error Page"),
 * lo que rompería la integración con Feign (que espera JSON).
 * @Slf4j: inyecta un logger (log.info, log.error, etc.) via Lombok.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura: ProductNotFoundException
     * Cuándo se lanza: GET /products/{id} con un ID que no existe
     * Respuesta: 404 NOT FOUND con el ErrorResponse en JSON
     * <p>
     * Cuando Feign recibe este 404, su ErrorDecoder lo procesa
     * y lanza ProductNotFoundException en orders-service también.
     */
    @ExceptionHandler(ProductExceptions.ProductNotFoundException.class)
    public ResponseEntity<ProductExceptions.ErrorResponse> handleNotFound(
            ProductExceptions.ProductNotFoundException ex,
            HttpServletRequest request) {  // HttpServletRequest → para incluir la URL en el error

        log.warn("Producto no encontrado: ID={}", ex.getProductId());

        var error = new ProductExceptions.ErrorResponse(
                HttpStatus.NOT_FOUND.value(),  // 404
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()        // ej: /products/99
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Captura: InsufficientStockException
     * Cuándo se lanza: PATCH /products/{id}/stock con cantidad mayor al stock disponible
     * Respuesta: 409 CONFLICT
     * <p>
     * orders-service recibirá este 409 y puede decidir cancelar la orden.
     */
    @ExceptionHandler(ProductExceptions.InsufficientStockException.class)
    public ResponseEntity<ProductExceptions.ErrorResponse> handleInsufficientStock(
            ProductExceptions.InsufficientStockException ex,
            HttpServletRequest request) {

        log.warn("Stock insuficiente: {}", ex.getMessage());

        var error = new ProductExceptions.ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_STOCK",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Captura: MethodArgumentNotValidException
     * Cuándo se lanza: body del request falla las validaciones @Valid
     * ej: POST /products sin campo "name"
     * Respuesta: 400 BAD REQUEST con lista de errores de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProductExceptions.ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Extrae todos los mensajes de error de validación de cada campo
        List<String> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                // "name: El nombre es obligatorio", "price: El precio debe ser mayor a 0"
                .map(FieldError::getDefaultMessage)
                .toList();

        log.warn("Error de validación: {}", validationErrors);

        var error = new ProductExceptions.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Error en los datos enviados",
                request.getRequestURI()
        ).withValidationErrors(validationErrors);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Captura: cualquier excepción no manejada por los handlers anteriores.
     * Es el "catch-all" que evita exponer stack traces al cliente.
     * Respuesta: 500 INTERNAL SERVER ERROR genérico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProductExceptions.ErrorResponse> handleGeneral(
            Exception ex,
            HttpServletRequest request) {

        // Loguea el stack trace completo → útil para debugging
        log.error("Error inesperado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        var error = new ProductExceptions.ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Ha ocurrido un error inesperado",  // mensaje genérico al cliente
                request.getRequestURI()
        );

        return ResponseEntity.internalServerError().body(error);
    }
}

package com.e_commerce.reviewsservice.Exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 - la reseña no existe
    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ReviewNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 409 Conflict - el índice único de Mongo detectó una reseña duplicada.
    // Este es EL caso que justifica todo el diseño del índice único:
    // el cliente recibe un 409 claro, no un 500 genérico de "algo falló en la BD".
    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateReviewException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 409 Conflict también, pero por una razón distinta: choque de @Version
    // (dos ediciones concurrentes). Mismo código HTTP, mensaje distinto,
    // para que el frontend pueda decidir "recarga y reintenta" en ambos casos.
    @ExceptionHandler(ReviewConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ReviewConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 400 - errores de @Valid en el CreateReviewRequest (rating fuera de 1-5, productId vacío, etc.)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // 500 - red de seguridad: cualquier excepción no prevista NO debe filtrar
    // detalles internos (stack trace, nombre de clases de Mongo, etc.) al cliente.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error no controlado en reviews-service", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

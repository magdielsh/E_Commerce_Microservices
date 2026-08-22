package com.e_commerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Servicio Temporalmente No Disponible");
        response.put("message", "El servicio de usuarios está tardando demasiado en responder o se encuentra fuera de línea. Por favor, inténtalo más tarde.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @PostMapping("/account-service")
    public ResponseEntity<Map<String, Object>> accountServicePOSTFallback() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Servicio Temporalmente No Disponible");
        response.put("message", "El servicio de autenticación está tardando demasiado en responder o se encuentra fuera de línea. Por favor, inténtalo más tarde.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/account-service")
    public ResponseEntity<Map<String, Object>> accountServiceGETFallback() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Servicio Temporalmente No Disponible");
        response.put("message", "El servicio de autenticación está tardando demasiado en responder o se encuentra fuera de línea. Por favor, inténtalo más tarde.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}

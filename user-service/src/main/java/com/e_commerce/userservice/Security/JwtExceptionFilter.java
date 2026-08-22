package com.e_commerce.userservice.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class JwtExceptionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            // Deja pasar la petición al siguiente filtro (JwtAuthenticationFilter)
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            // Si el filtro de JWT lanza una excepción, la capturamos aquí
            setErrorResponse(HttpStatus.UNAUTHORIZED, response, request, ex);
        }
    }

    private void setErrorResponse(HttpStatus status, HttpServletResponse response, HttpServletRequest request, Throwable ex) {
        response.setStatus(status.value());
        response.setContentType("application/json"); // Asegura que la respuesta sea JSON
        response.setCharacterEncoding("UTF-8");

        // Creamos una estructura limpia para el cliente
        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("status", status.value());
        errorDetails.put("error", status.getReasonPhrase());

        // Personaliza el mensaje según el tipo de error de la librería io.jsonwebtoken
        String message = "Token inválido o corrupto";
        if (ex.getMessage().contains("expired")) {
            message = "El token JWT ha expirado. Por favor, solicita uno nuevo.";
        } else if (ex.getMessage().contains("signature")) {
            message = "La firma del token no es válida.";
        }

        errorDetails.put("message", message);
        errorDetails.put("path", ((HttpServletRequest) request).getRequestURI());

        try {
            // Convertimos el mapa a texto JSON usando Jackson (el serializador por defecto de Spring)
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(errorDetails);
            response.getWriter().write(jsonResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


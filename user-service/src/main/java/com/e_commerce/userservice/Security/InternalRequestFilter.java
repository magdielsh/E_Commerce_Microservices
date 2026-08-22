package com.e_commerce.userservice.Security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Slf4j
@Component
@Getter
@Setter
@ConfigurationProperties(prefix="public")
public class InternalRequestFilter extends OncePerRequestFilter {

    private List<String> routes;

    @Value("${gateway.secret}")
    private String gateway_Secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if (isPublicRoute(request.getRequestURI())) {
            log.info("Ruta pública {}, dejando pasar", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String authenticated = request.getHeader("X-Authenticated");
        String userEmail     = request.getHeader("X-User-Email");
        String rolesHeader   = request.getHeader("X-User-Role"); // "ROLE_ADMIN,ROLE_USER"
        String gatewaySecret   = request.getHeader("X-Gateway-Secret");

        if (!gatewaySecret.equalsIgnoreCase(gateway_Secret)) {
            log.warn("⛔ Petición directa rechazada, procedencia no confirmada, (sin gateway): {} {}",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Acceso solo permitido a través del gateway\"}"
            );
            return; // cortamos la cadena: el controller nunca se ejecuta
        }

        // Si NO hay cabecera X-Authenticated: true, la petición no pasó por el gateway
        if (!"true".equals(authenticated) || userEmail == null || userEmail.isBlank()) {
            log.warn("⛔ Petición directa rechazada, datos corruptos (sin gateway): {} {}",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Acceso solo permitido a través del gateway\"}"
            );
            return; // cortamos la cadena: el controller nunca se ejecuta
        }

        // Convertir el header de roles en GrantedAuthority
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (rolesHeader != null && !rolesHeader.isBlank()) {
            authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .map(SimpleGrantedAuthority::new) // ya vienen con "ROLE_"
                    .toList();
        }

        // Registrar en el SecurityContext para que hasRole() funcione
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userEmail,    // principal
                        null,         // credentials (no necesarias aquí)
                        authorities   // roles
                );
        // Toma el objeto request (la petición del usuario) y extrae datos clave,
        // principalmente la dirección IP remota y el ID de la sesión HTTP.
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        log.info("✅ Acceso permitido: {} | roles: {}", userEmail, authorities);

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // Todo correcto: continuamos hacia el controller
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext(); // limpia al terminar el request
        }
    }

    private boolean isPublicRoute(String path) {
        return routes.stream()
                .anyMatch(publicPath -> path.startsWith(publicPath));
    }
}

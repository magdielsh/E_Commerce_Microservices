package com.e_commerce.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// GlobalFilter: se ejecuta en TODAS las peticiones que pasan por el gateway.
// No hace falta registrarlo; Spring Cloud Gateway lo detecta automáticamente.
//
// Ordered: el método getOrder() define la prioridad.
// -1 significa que se ejecuta antes de los filtros del gateway por defecto.
// ─────────────────────────────────────────────────────────────────────────────
@Slf4j
@Component
@Getter
@Setter
@RequiredArgsConstructor
@ConfigurationProperties(prefix="public")
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;

    // Lista de rutas públicas leída del application.yml
    private List<String> routes;

    // ─────────────────────────────────────────────────────────────────────
    // filter() es el método principal del filtro.
    // Devuelve Mono<Void> porque Gateway es reactivo (no bloqueante).
    //
    // ServerWebExchange: representa el par petición/respuesta.
    // GatewayFilterChain: la cadena de filtros. Llamar a chain.filter()
    //   significa "sigue adelante, enruta la petición al microservicio".
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        log.info("Gateway recibe: {} {}", exchange.getRequest().getMethod(), path);

        // ─── PASO 1: ¿Es una ruta pública? ───────────────────────────────
        // Si la ruta está en la lista de rutas públicas (ej: /auth/login),
        // dejamos pasar sin validar ningún token.
        if (isPublicRoute(path)) {
            log.info("Ruta pública {}, dejando pasar", path);
            return chain.filter(exchange);
        }

        // ─── PASO 2: Extraer la cabecera Authorization ───────────────────
        String authHeader = exchange.getRequest()
            .getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        // Si no hay cabecera o no empieza por "Bearer ", rechazamos con 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Petición sin token a ruta protegida: {}", path);
            return unauthorized(exchange, "Token requerido");
        }

        // Extraemos el token quitando el prefijo "Bearer " (7 caracteres)
        String token = authHeader.substring(7);

        // ─── PASO 3: Validar el JWT ───────────────────────────────────────
        Claims claims;
        try {
            // validateAndExtract lanza JwtException si:
            //   - La firma no es válida (token manipulado)
            //   - El token ha expirado (exp < now)
            //   - El token está malformado
            claims = jwtValidator.validateAndExtract(token);
        } catch (JwtException e) {
            log.warn("Token inválido para {}: {}", path, e.getMessage());
            return unauthorized(exchange, "Token inválido o expirado");
        }

        // ─── PASO 4: Extraer datos del payload del JWT ────────────────────
        // El payload es el bloque central del JWT (decodificable sin clave).
        // Contiene los "claims": datos que el Auth Service metió al firmar.
        String userEmail = claims.getSubject();          // campo "sub"
        List<String> roles  = claims.get("role", List.class); // campo "role"
        String userRole = String.join(",", roles);

        log.info("JWT válido: user={}, role={}", userEmail, userRole);

        // ─── PASO 5: Mutar la petición añadiendo cabeceras internas ───────
        // Este es el momento clave: transformamos el JWT en cabeceras HTTP
        // que los microservicios internos podrán leer directamente.
        //
        // exchange.getRequest().mutate() crea una COPIA INMUTABLE de la petición
        // con las nuevas cabeceras añadidas. La petición original no se modifica.
        //
        // Los microservicios internos recibirán estas cabeceras y podrán
        // saber quién hace la petición SIN necesitar Spring Security ni JWT.
        ServerHttpRequest mutatedRequest = exchange.getRequest()
            .mutate()
            // X-User-Email: el email del usuario autenticado
            // Los controllers lo leerán con @RequestHeader("X-User-Email")
            .header("X-User-Email", userEmail)

            // X-User-Role: el rol del usuario (ROLE_USER, ROLE_ADMIN...)
            // Útil para autorización manual en los microservicios
            .header("X-User-Role", userRole)

            // X-Authenticated: cabecera de control. Si un microservicio
            // recibe una petición SIN esta cabecera, sabe que llegó
            // directamente (saltándose el gateway) → puede rechazarla.
            .header("X-Authenticated", "true")

            // Eliminamos la cabecera Authorization original del reenvío.
            // Los microservicios internos NO necesitan el JWT completo;
            // ya tienen la info extraída en las cabeceras de arriba.
            // Así reducimos tamaño de petición y evitamos que los servicios
            // internos dependan del formato JWT.
            .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
            .build();

        // Reemplazamos la petición en el exchange y continuamos la cadena
        log.info("Continuando hacia microservicio: " + path);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // Este filtro se ejecuta con prioridad -1 (antes que los filtros por defecto)
    @Override
    public int getOrder() {
        return -1;
    }

    // Comprueba si la ruta actual está en la lista de rutas públicas
    private boolean isPublicRoute(String path) {
        return routes.stream()
            .anyMatch(publicPath -> path.startsWith(publicPath));
    }

    // Responde directamente con 401 sin llegar al microservicio destino.
    // exchange.getResponse().setComplete() cierra la respuesta.
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        var buffer = exchange.getResponse().bufferFactory()
            .wrap(("{\"error\":\"" + message + "\"}").getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

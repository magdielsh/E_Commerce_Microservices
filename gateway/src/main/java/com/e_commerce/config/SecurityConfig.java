package com.e_commerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

// ─────────────────────────────────────────────────────────────────────────────
// ¿Por qué necesitamos esta clase si ya tenemos nuestro filtro manual?
//
// Al incluir spring-boot-starter-security, Spring Boot activa por defecto
// su propio sistema de seguridad reactivo que:
//   1. Bloquea todas las peticiones sin autenticación básica HTTP
//   2. Genera una contraseña aleatoria en consola al arrancar
//   3. Redirige a un formulario de login
//
// Nosotros NO queremos nada de eso: ya controlamos la seguridad con
// JwtAuthGatewayFilter. Esta clase deshabilita el sistema por defecto
// y deja que nuestro filtro sea el único punto de control.
// ─────────────────────────────────────────────────────────────────────────────

@Configuration
@EnableWebFluxSecurity  // Versión reactiva de @EnableWebSecurity (para WebFlux/Gateway)
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // CSRF no aplica en APIs stateless con JWT (no hay cookies de sesión)
            .csrf(csrf -> csrf.disable())

            // Deshabilitamos la autenticación HTTP Basic (usuario/contraseña en cabecera)
            .httpBasic(basic -> basic.disable())

            // Deshabilitamos el formulario de login generado por Spring Security
            .formLogin(form -> form.disable())

            // PERMITIMOS TODAS LAS RUTAS aquí.
            // Esto puede parecer inseguro, pero nuestro JwtAuthGatewayFilter
            // (que se ejecuta ANTES de llegar aquí) ya ha rechazado las
            // peticiones sin token válido con 401.
            // Si llegamos a este punto, el filtro ya aprobó la petición.
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            )
            .build();
    }
}

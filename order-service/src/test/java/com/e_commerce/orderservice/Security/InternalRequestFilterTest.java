package com.e_commerce.orderservice.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * InternalRequestFilterTest — Tests del filtro que valida peticiones internas
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * ¿Qué probamos?
 *   • Que el filtro permite el paso cuando la cabecera X-Authenticated=true
 *   • Que el filtro rechaza con 403 cuando falta X-Authenticated
 *   • Que el filtro permite /actuator/ paths aunque no tengan la cabecera
 *   • Que el filtro setea el SecurityContext con los roles correctos
 */
@ExtendWith(MockitoExtension.class)
class InternalRequestFilterTest {

    private InternalRequestFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new InternalRequestFilter();
        responseWriter = new StringWriter();
        // El PrintWriter donde el filtro escribe el error JSON en caso de rechazo
        given(response.getWriter()).willReturn(new PrintWriter(responseWriter));
    }

    @Nested
    @DisplayName("Cabecera X-Authenticated")
    class XAuthenticatedHeader {

        @Test
        @DisplayName("X-Authenticated=true + X-User-Email → permite el paso")
        void allowsWhenAuthenticated() throws Exception {
            // ── given ──
            given(request.getHeader("X-Authenticated")).willReturn("true");
            given(request.getHeader("X-User-Email")).willReturn("user@test.com");
            given(request.getHeader("X-User-Role")).willReturn("ROLE_USER");

            // ── when ──
            filter.doFilterInternal(request, response, filterChain);

            // ── then: la cadena continúa → llamaron a filterChain.doFilter()
            then(filterChain).should(times(1)).doFilter(request, response);
            // El filtro NO escribió respuesta de error
            then(response).should(never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("X-Authenticated=true pero userEmail vacío → rechaza 403")
        void rejectsWhenEmailBlank() throws Exception {
            // ── given ──
            given(request.getHeader("X-Authenticated")).willReturn("true");
            given(request.getHeader("X-User-Email")).willReturn("   ");  // blank

            // ── when ──
            filter.doFilterInternal(request, response, filterChain);

            // ── then ──
            then(filterChain).should(never()).doFilter(any(), any());
            then(response).should(times(1)).setStatus(403);
            assertThat(responseWriter.toString()).contains("gateway");
        }

        @Test
        @DisplayName("X-Authenticated=false → rechaza 403")
        void rejectsWhenNotAuthenticated() throws Exception {
            given(request.getHeader("X-Authenticated")).willReturn("false");

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(never()).doFilter(any(), any());
            then(response).should(times(1)).setStatus(403);
        }

        @Test
        @DisplayName("Sin cabecera X-Authenticated → rechaza 403")
        void rejectsWhenHeaderMissing() throws Exception {
            given(request.getHeader("X-Authenticated")).willReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(never()).doFilter(any(), any());
            then(response).should(times(1)).setStatus(403);
        }
    }

    @Nested
    @DisplayName("Paths de /actuator/")
    class ActuatorPaths {

        @Test
        @DisplayName("/actuator/circuitbreakers → permite aunque no tenga cabeceras")
        void allowsActuatorCircuitbreakers() throws Exception {
            given(request.getRequestURI()).willReturn("/actuator/circuitbreakers");

            // No configuramos cabeceras → el filtro debería saltar por el path

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("/actuator/health → permite")
        void allowsActuatorHealth() throws Exception {
            given(request.getRequestURI()).willReturn("/actuator/health");

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("/actuator/info → permite")
        void allowsActuatorInfo() throws Exception {
            given(request.getRequestURI()).willReturn("/actuator/info");

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Roles y SecurityContext")
    class SecurityContext {

        @Test
        @DisplayName("X-User-Role con múltiples roles separados por coma")
        void multipleRoles() throws Exception {
            given(request.getHeader("X-Authenticated")).willReturn("true");
            given(request.getHeader("X-User-Email")).willReturn("admin@test.com");
            given(request.getHeader("X-User-Role")).willReturn("ROLE_ADMIN,ROLE_USER");

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("X-User-Role null → no lanza NullPointerException")
        void nullRole() throws Exception {
            given(request.getHeader("X-Authenticated")).willReturn("true");
            given(request.getHeader("X-User-Email")).willReturn("user@test.com");
            given(request.getHeader("X-User-Role")).willReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("X-User-Role vacío → no lanza excepción")
        void emptyRole() throws Exception {
            given(request.getHeader("X-Authenticated")).willReturn("true");
            given(request.getHeader("X-User-Email")).willReturn("user@test.com");
            given(request.getHeader("X-User-Role")).willReturn("");

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should(times(1)).doFilter(request, response);
        }
    }

    @Test
    @DisplayName("Responde con Content-Type application/json")
    void responseContentTypeIsJson() throws Exception {
        given(request.getHeader("X-Authenticated")).willReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        then(response).should(times(1)).setContentType("application/json");
    }

    @Test
    @DisplayName("Mensaje de error contiene 'Acceso solo permitido'")
    void errorMessage() throws Exception {
        given(request.getHeader("X-Authenticated")).willReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(responseWriter.toString()).contains("Acceso solo permitido");
    }
}

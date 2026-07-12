package com.e_commerce.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

// El gateway SOLO valida tokens, nunca los genera.
// Generarlos es responsabilidad exclusiva del Account Service.
@Component
public class JwtValidator {

    @Value("${jwt.secret}")
    private String secret;

    // Devuelve los claims si el token es válido.
    // Lanza JwtException si la firma es inválida o el token expiró.
    // Spring Cloud Gateway es reactivo, así que este método síncrono
    // solo se llama dentro del contexto del filtro (Mono/Flux).
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

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

    // OJO, no verifica si el usuario esta bloqueado o el token invalidado en manualmente en una lista negra (por ejemlo en Redis)
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

    // Para que funcione el tamaño mínimo de seguridad criptográfica que exige el algoritmo HS256
    // El secreto decodificado de Base64 debe tener al menos 256 bits (32 bytes) de longitud
    // si es muy corto, el metodo Keys.hmacShaKeyFor(keyBytes) lanzará un error de tipo WeakKeyException e impedirá que tu aplicación inicie
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

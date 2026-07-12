package com.e_commerce.acountservice.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;
    // ↑ 900000 ms = 15 minutos

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 1: Extraer el username (email) del token
    // ─────────────────────────────────────────────────────────────────
    public String extractUsername(String token) {
        // Claims.SUBJECT es el campo "sub" del payload JWT
        // Ahí guardamos el email del usuario cuando creamos el token
        return extractClaim(token, Claims::getSubject);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 2: Extractor genérico de claims (datos del payload)
    // ─────────────────────────────────────────────────────────────────
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        // Primero extraemos TODOS los claims del token
        final Claims claims = extractAllClaims(token);
        // Luego aplicamos la función específica que nos piden
        // Ejemplo: Claims::getSubject extrae solo el "sub"
        //          Claims::getExpiration extrae solo la fecha de expiración
        return claimsResolver.apply(claims);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 3: Generar un Access Token (sin claims extra)
    // ─────────────────────────────────────────────────────────────────
    public String generateToken(UserDetails userDetails) {
        // Llamamos al método completo pero sin claims adicionales
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", roles);
        return generateToken(extraClaims, userDetails);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 4: Generar un Access Token (con claims extra opcionales)
    // ─────────────────────────────────────────────────────────────────
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 5: Constructor interno del token — aquí pasa la magia
    // ─────────────────────────────────────────────────────────────────
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts.builder()
                // claims(): establece claims personalizados (ej: roles, nombre)
                // Deben ir ANTES de subject() o se sobreescriben
                .claims(extraClaims)

                // subject(): el "dueño" del token — guardamos el email
                // Es el campo "sub" en el payload del JWT
                .subject(userDetails.getUsername())

                // issuedAt(): cuándo se creó el token — campo "iat" en JWT
                .issuedAt(new Date(System.currentTimeMillis()))

                // expiration(): cuándo expira — campo "exp" en JWT
                // currentTimeMillis() + expiration = fecha futura
                .expiration(new Date(System.currentTimeMillis() + expiration))

                // signWith(): firma el token con nuestra clave secreta
                // En 0.12.x ya NO necesitas especificar el algoritmo manualmente
                // JJWT lo detecta automáticamente desde el tipo de clave
                .signWith(getSignInKey())

                // compact(): serializa todo y genera el String final
                // "eyJhbGci..." — el token que enviamos al cliente
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 6: Validar si un token es válido para un usuario
    // ─────────────────────────────────────────────────────────────────
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        // El token es válido si:
        // 1. El username del token coincide con el usuario actual
        // 2. El token no ha expirado
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 7: Verificar si el token expiró
    // ─────────────────────────────────────────────────────────────────
    private boolean isTokenExpired(String token) {
        // Extrae la fecha de expiración y la compara con ahora
        // before(new Date()) = "¿la expiración es ANTES de ahora?" = expirado
        return extractExpiration(token).before(new Date());
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 8: Extraer la fecha de expiración del token
    // ─────────────────────────────────────────────────────────────────
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 9: Parsear el token y obtener TODOS los claims
    // ─────────────────────────────────────────────────────────────────
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // verifyWith(): le decimos con qué clave verificar la firma
                // Si alguien alteró el token, la firma no coincidirá y lanzará excepción
                .verifyWith(getSignInKey())
                .build()
                // parseSignedClaims(): parsea el token y verifica firma + expiración
                // En 0.12.x se renombró de parseClaimsJws() a parseSignedClaims()
                .parseSignedClaims(token)
                // getPayload(): retorna el objeto Claims con todos los datos del payload
                .getPayload();
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODO 10: Generar la clave criptográfica desde el secret en Base64
    // ─────────────────────────────────────────────────────────────────
    private SecretKey getSignInKey() {
        // Decoders.BASE64.decode(): convierte el String Base64 a array de bytes
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);

        // Keys.hmacShaKeyFor(): crea una clave HMAC-SHA apropiada
        // Si son 256 bits → HS256, 384 bits → HS384, 512 bits → HS512
        // JJWT elige automáticamente el algoritmo más fuerte posible
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

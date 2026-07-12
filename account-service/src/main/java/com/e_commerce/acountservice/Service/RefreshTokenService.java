package com.e_commerce.acountservice.Service;

import com.e_commerce.acountservice.Entity.RefreshToken;
import com.e_commerce.acountservice.Entity.UserEntity;
import com.e_commerce.acountservice.Repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${jwt.time.refresh-expiration}")
    private Long refreshTokenDurationMs;
    // ↑ 604800000 ms = 7 días

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // Crear y guardar un nuevo Refresh Token en BD
    // ─────────────────────────────────────────────────────────────────
    @Transactional
    public RefreshToken createRefreshToken(UserEntity user) {
        // Primero eliminamos el token anterior del usuario
        // (Un usuario = un refresh token activo a la vez en este diseño)
        refreshTokenRepository.deleteByUsersId(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .users(user)
                // UUID.randomUUID() genera un string aleatorio y único
                // No es un JWT — es solo un identificador seguro que guardamos en BD
                .token(UUID.randomUUID().toString())
                // Instant.now() + duración = fecha exacta de expiración
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .build();

        // Lo guardamos en la tabla refresh_tokens de PostgreSQL
        return refreshTokenRepository.save(refreshToken);
    }

    // ─────────────────────────────────────────────────────────────────
    // Verificar si el Refresh Token no ha expirado
    // ─────────────────────────────────────────────────────────────────
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            // El token expiró → lo eliminamos de BD y lanzamos excepción
            refreshTokenRepository.delete(token);
            throw new RuntimeException(
                    "El refresh token expiró. Por favor inicia sesión nuevamente."
            );
        }
        return token; // Token válido → lo retornamos para continuar el flujo
    }

    // ─────────────────────────────────────────────────────────────────
    // Buscar un Refresh Token por su valor string
    // ─────────────────────────────────────────────────────────────────
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    // ─────────────────────────────────────────────────────────────────
    // Eliminar todos los tokens del usuario (logout)
    // ─────────────────────────────────────────────────────────────────
    @Transactional
    // ↑ @Transactional es necesario para operaciones de delete en Spring Data
    public void deleteByUser(UserEntity user) {
        refreshTokenRepository.deleteByUsersId(user.getId());
    }
}

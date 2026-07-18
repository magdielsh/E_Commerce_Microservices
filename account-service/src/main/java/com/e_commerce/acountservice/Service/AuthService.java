package com.e_commerce.acountservice.Service;

import com.e_commerce.acountservice.Dto.AuthRequestDTO;
import com.e_commerce.acountservice.Dto.AuthResponseDTO;
import com.e_commerce.acountservice.Entity.RefreshToken;
import com.e_commerce.acountservice.Entity.UserEntity;
import com.e_commerce.acountservice.Repository.UserRepository;
import com.e_commerce.acountservice.Security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
   // private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponseDTO login(AuthRequestDTO request) {
        // authenticate() hace el trabajo completo
        // 1. Carga el usuario por email (via UserDetailsService)
        // 2. Compara el password con BCrypt
        // 3. Si falla → lanza BadCredentialsException automáticamente
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Si llegamos aquí, las credenciales son correctas
        // Cargamos el usuario de la BD para generar los tokens
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Generamos el Access Token (JWT, 15 minutos)
        String accessToken = jwtService.generateToken(user);



        // Generamos y guardamos el Refresh Token en BD (UUID, 7 días)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    public AuthResponseDTO refreshToken(String requestRefreshToken) {
        // Buscamos el refresh token en BD
        return refreshTokenService.findByToken(requestRefreshToken)
                // Verificamos que no haya expirado (si expiró, lanza excepción)
                .map(refreshTokenService::verifyExpiration)
                // Obtenemos el usuario dueño del token
                .map(RefreshToken::getUsers)
                // Generamos un nuevo Access Token para ese usuario
                .map(user -> {
                    String newAccessToken = jwtService.generateToken(user);
                    return AuthResponseDTO.builder()
                            .accessToken(newAccessToken)
                            // Devolvemos el MISMO refresh token (no lo renovamos)
                            .refreshToken(requestRefreshToken)
                            .build();
                })
                .orElseThrow(() -> new RuntimeException("Refresh token no encontrado en BD"));
    }

    public void logout(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        // Eliminamos el refresh token de BD — el usuario queda desconectado
        refreshTokenService.deleteByUser(user);
        // El Access Token no podemos invalidarlo (es stateless)
        // Por eso le damos duración corta (15 min) — expira solo
    }

}

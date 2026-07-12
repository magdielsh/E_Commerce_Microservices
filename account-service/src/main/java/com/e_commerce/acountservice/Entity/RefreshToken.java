package com.e_commerce.acountservice.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@Table(name = "refresh_tokens")
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;
    // ↑ El valor del refresh token (un UUID aleatorio, no un JWT)

    @Column(nullable = false)
    private Instant expiryDate;
    // ↑ Instant es el tipo correcto para fechas de expiración en Java moderno
    //   Representa un punto exacto en el tiempo (UTC)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    private UserEntity users;
    // ↑ Relación con el usuario dueño de este token
    //   Un usuario puede tener múltiples refresh tokens (desde distintos dispositivos)
    //   FetchType.LAZY = no carga el usuario de BD hasta que se necesite (mejor rendimiento)
}

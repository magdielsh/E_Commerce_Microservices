package com.e_commerce.acountservice.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String accessToken;   // JWT de corta duración (15 min)
    private String refreshToken;  // UUID de larga duración (7 días), guardado en BD
}
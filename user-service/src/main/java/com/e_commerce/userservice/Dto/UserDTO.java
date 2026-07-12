package com.e_commerce.userservice.Dto;

import com.e_commerce.userservice.Entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Representación de un Usuario")
public class UserDTO {

    @NotNull
    @Schema(description = "Identificador unico", example = "1")
    private Long id;

    @Email
    @NotBlank
    @Schema(description = "Email del usuario", example = "email@domain.com")
    private String email;

    @NotBlank
    @Schema(description = "Nombre de Usuario", example = "magdielsh")
    private String username;

    @NotBlank
    @Schema(description = "Contraseña del usuario", example = "Mipassword*123")
    private String password;

    @NotEmpty
    @Schema(description = "Roles del usuario", example = "USER, ADMIN")
    private Set<Role> roles;
}

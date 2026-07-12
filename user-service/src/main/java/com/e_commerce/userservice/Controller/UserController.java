package com.e_commerce.userservice.Controller;

import com.e_commerce.userservice.Dto.CreateUserDTO;
import com.e_commerce.userservice.Dto.RegisterRequestDTO;
import com.e_commerce.userservice.Dto.UserDTO;
import com.e_commerce.userservice.Service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("v1/api/users")
@AllArgsConstructor
@Tag(name = "Users", description = "Operaciones sobre Usuarios")
public class UserController {

    private final UserService userService;

    @GetMapping("/findUser")
    //@PreAuthorize("hasRole('USER')")
    @Operation(summary = "Obtener Usuario por su nombre", description = "Devielve los detalles de un usuario especifico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario Encontrado",
            content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no Encontrado", content = @Content)
    })
    public ResponseEntity<UserDTO> findUserByUserName (
            @RequestParam(name = "userName", defaultValue = "") String userName){

        return ResponseEntity.status(HttpStatus.OK).body(userService.findUserByUserName(userName));
    }

    @PostMapping("/saveUser")
    public ResponseEntity<UserDTO> saveUser(@Valid @RequestBody CreateUserDTO createUserDTO){

        return ResponseEntity.status(HttpStatus.CREATED).body(userService.saveUser(createUserDTO));
    }

    @PutMapping("/updateUser")
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody UserDTO userDTO){

        return ResponseEntity.status(HttpStatus.OK).body(userService.updateUser(userDTO));
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar un usuario", description = "Se registra usurio en BD y se envia códifgo de verificación")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Email Existente", content = @Content)
    })
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.register(request));
    }

    // GET /api/auth/verify?token=uuid
    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(
            @RequestParam String token
            // ↑ Spring extrae el parámetro ?token=... de la URL automáticamente
    ) {
        return ResponseEntity.ok(userService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(
            @RequestParam String email
            // ↑ El cliente envía: POST /api/auth/resend-verification?email=juan@empresa.com
    ) {
        return ResponseEntity.ok(userService.resendVerificationEmail(email));
    }

    @DeleteMapping("deleteUser/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long userId){

        userService.deleteUser(userId);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Usuario Eliminado");
        result.put("usuario con ID: ", userId.toString());

        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}

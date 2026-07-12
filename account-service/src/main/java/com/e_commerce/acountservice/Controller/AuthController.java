package com.e_commerce.acountservice.Controller;

import com.e_commerce.acountservice.Dto.AuthRequestDTO;
import com.e_commerce.acountservice.Dto.AuthResponseDTO;
import com.e_commerce.acountservice.Service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("v1/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Recibe: { "refreshToken": "uuid-del-token" }
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponseDTO> refreshToken(
            @RequestBody Map<String, String> request
    ) {
        return ResponseEntity.ok(
                authService.refreshToken(request.get("refreshToken"))
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestParam String email
    ) {
        authService.logout(email);
        return ResponseEntity.ok("Sesión cerrada correctamente");
    }


}

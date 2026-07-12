package com.e_commerce.acountservice.Config;


import com.e_commerce.acountservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        // Lambda que le dice a Spring Security cómo cargar un usuario desde BD
        // Spring Security llama a esto cuando necesita verificar credenciales
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username
                ));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // DaoAuthenticationProvider: implementación que usa BD para autenticar
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        // Le decimos cómo cargar usuarios (el bean de arriba)
        authProvider.setUserDetailsService(userDetailsService());

        // Le decimos cómo verificar passwords (BCrypt)
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        // AuthenticationManager es el componente central que coordina la autenticación
        // Lo necesitamos en AuthService para autenticar en el login
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt es el algoritmo estándar para hashear passwords
        // Agrega un "salt" automáticamente, haciendo imposibles los ataques de rainbow table
        return new BCryptPasswordEncoder();
    }
}

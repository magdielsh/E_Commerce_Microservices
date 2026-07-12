package com.e_commerce.userservice.Service;

import com.e_commerce.userservice.Dto.CreateUserDTO;
import com.e_commerce.userservice.Dto.RegisterRequestDTO;
import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import com.e_commerce.userservice.Dto.UserDTO;
import com.e_commerce.userservice.Entity.OutboxEvent;
import com.e_commerce.userservice.Entity.Role;
import com.e_commerce.userservice.Entity.UserEntity;
import com.e_commerce.userservice.Entity.VerificationToken;
import com.e_commerce.userservice.Enums.ERole;
import com.e_commerce.userservice.Exceptions.BusinessException;
import com.e_commerce.userservice.Exceptions.EmailAlreadyExistsException;
import com.e_commerce.userservice.Interfaces.IUserInterface;
import com.e_commerce.userservice.Kafka.UserEventProducer;
import com.e_commerce.userservice.Repository.OutboxEventRepository;
import com.e_commerce.userservice.Repository.UserRepository;
import com.e_commerce.userservice.Repository.VerificationTokenRepository;
import com.e_commerce.userservice.Util.Mapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class UserService implements IUserInterface {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final UserEventProducer userEventProducer;

    public static final String EVENTO_MENSAJE_CREADO   = "MensajeCreado";
    public static final String EVENTO_MENSAJE_LEIDO    = "MensajeLeido";
    public static final String EVENTO_MENSAJE_ELIMINADO = "MensajeEliminado";
    public static final String AGGREGATE_TYPE = "user-created";

    @Override
    public UserDTO findUserByUserName(String userName) {

        return Mapper.userToDTO(userRepository.findByEmail(userName)
                .orElseThrow(() -> new RuntimeException("Usuario no Encontrado desde UserService")));
    }

    @Override
    @Transactional
    public UserDTO saveUser(CreateUserDTO createUserDTO) {

        Set<Role> roles = createUserDTO.getRoles()
                .stream()
                .map(role -> Role.builder()
                        .name(ERole.valueOf(role))
                        .build())
                .collect(Collectors.toSet());


        UserEntity userEntity = UserEntity.builder()
                .username(createUserDTO.getUsername())
                .password(passwordEncoder.encode(createUserDTO.getPassword()))
                .email(createUserDTO.getEmail())
                .roles(roles)
                .build();

        return Mapper.userToDTO(userRepository.save(userEntity));
    }

    @Override
    @Transactional
    public UserDTO updateUser(UserDTO userDTO) {

        UserEntity user = userRepository.findByEmail(userDTO.getUsername()).get();

        Set<Role> roles = userDTO.getRoles()
                .stream()
                .map(role -> Role.builder()
                        .id(role.getId())
                        .name(ERole.valueOf(role.getName().name()))
                        .build())
                .collect(Collectors.toSet());

        user.setUsername(userDTO.getUsername());
        user.setPassword(userDTO.getPassword());
        user.setEmail(userDTO.getEmail());
        user.setRoles(roles);

        return Mapper.userToDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {

        userRepository.deleteById(userId);

    }

// ─────────────────────────────────────────────────────────────────
// REGISTRO
// ─────────────────────────────────────────────────────────────────
    @Transactional
    public String register(RegisterRequestDTO request) {

        // 1. Verificar que el email no esté ya registrado
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
            // ↑ GlobalExceptionHandler lo captura → 409 Conflict
        }

        Set<Role> role = new HashSet<>();
        role.add(Role.builder().name(ERole.valueOf("USER")).build());

        // 2. Crear el usuario con enabled=false
        UserEntity user = userRepository.save(UserEntity.builder()
                .username(request.getNombre())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(role)
                // ↑ Por defecto todos los registrados son USER
                .enabled(false)
                // ↑ No puede hacer login hasta verificar el email
                .build());

        // 3. Crear el token de verificación
        VerificationToken verificationToken = VerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(verificationToken);

        // 4. Crear el evento que se guardara en OutBox
        UserCreatedValidationEvent userCreatedValidationEvent = new UserCreatedValidationEvent(
                verificationToken.getToken(),
                user.getEmail(),
                user.getId(),
                Instant.now());

        outboxEventRepository.save(OutboxEvent.create(
                UUID.randomUUID(),
                AGGREGATE_TYPE,
                userCreatedValidationEvent.userId().toString(),
                EVENTO_MENSAJE_CREADO,
                userCreatedValidationEvent,
                userCreatedValidationEvent.userId().toString()
        ));

        // docker exec -it kafka_mensajes kafka-topics --bootstrap-server localhost:9092 --list

        // 4. Send the message with Kafka for the Microservice Notification send Email
        //userEventProducer.publishUserVerification(userCreatedValidationEvent);

        //emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken());

        return "Registro exitoso. Se te enviara un email para activar tu cuenta.";
    }

// ─────────────────────────────────────────────────────────────────
// VERIFICACIÓN DE EMAIL
// ─────────────────────────────────────────────────────────────────
    @Transactional
    public String verifyEmail(String token) {

        // 1. Buscar el token en BD
        VerificationToken verificationToken = verificationTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new BusinessException(
                        "Token de verificación inválido o ya fue usado."));

        // 2. Verificar que no haya expirado
        if (verificationToken.getExpiryDate().isBefore(Instant.now())) {
            verificationTokenRepository.delete(verificationToken);
            // ↑ Limpiamos el token expirado de la BD
            throw new BusinessException(
                    "El enlace de verificación expiró. Solicita uno nuevo.");
        }

        // 3. Activar la cuenta del usuario
        UserEntity user = verificationToken.getUser();
        user.setEnabled(true);
        // ↑ Ahora isEnabled() retorna true → Spring Security permite el login
        userRepository.save(user);

        // 4. Eliminar el token — ya cumplió su propósito
        verificationTokenRepository.delete(verificationToken);

        return "Cuenta verificada exitosamente. Ya puedes iniciar sesión.";
    }

    // ─────────────────────────────────────────────────────────────────
    // REENVIO DE EMAIL
    // ─────────────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional
    public String resendVerificationEmail(String email) {

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado con Email: " + email));

        if (user.isEnabled()) {
            throw new BusinessException("Esta cuenta ya está verificada.");
        }

        verificationTokenRepository.deleteByUser(user);

        // 4. Crear nuevo token con 24h de vigencia
        VerificationToken newToken = VerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        verificationTokenRepository.save(newToken);

        //emailService.sendVerificationEmail(user.getEmail(), newToken.getToken());

        return "Email de verificación reenviado. Revisa tu bandeja de entrada.";
    }

    /**
     * Serializa el payload a JSON.
     * Envuelto en un método privado para centralizar el manejo de errores
     * de serialización y evitar repetición de código.
     *
     * JsonProcessingException es checked, pero la convertimos a RuntimeException
     * para que Spring la incluya en el rollback de la transacción automáticamente.
     * (Solo las RuntimeException hacen rollback por defecto en @Transactional)
     */
    private String serializarPayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Si no podemos serializar el payload, NO queremos guardar el mensaje.
            // Hacemos el rollback lanzando RuntimeException.
            throw new IllegalStateException("Error serializando payload del evento outbox", e);
        }
    }
}

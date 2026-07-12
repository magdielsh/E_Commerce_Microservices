package com.e_commerce.userservice.Util;


import com.e_commerce.userservice.Dto.UserDTO;
import com.e_commerce.userservice.Entity.Role;
import com.e_commerce.userservice.Entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;

import java.util.Set;
import java.util.stream.Collectors;


public class Mapper {

    // Jackson config — registrar módulos necesarios
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601 legible
                .build();
    }

    /**
     * Mapper UserEntity to UserDTO
     */
    public static UserDTO userToDTO (UserEntity userEntity){

        if (userEntity == null) return null;

        Set<Role> roles = userEntity.getRoles()
                .stream()
                .map(role -> Role.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .build())
                .collect(Collectors.toSet());

        return new UserDTO(
                userEntity.getId(),
                userEntity.getEmail(),
                userEntity.getUsername(),
                userEntity.getPassword(),
                roles
        );
    }
}

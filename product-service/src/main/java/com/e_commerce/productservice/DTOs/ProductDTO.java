package com.e_commerce.productservice.DTOs;

import com.e_commerce.productservice.Entity.ProductEntity;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class ProductDTO {


    /**
     * Response: lo que devolvemos al cliente (y a orders-service vía Feign).
     * Solo campos seguros — nunca devuelvas costos internos, IDs de proveedor, etc.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer stockQuantity;
        private String category;
        private boolean active;

        /**
         * Método de fábrica estático: convierte la entidad al DTO de respuesta.
         * Centraliza el mapeo → si el modelo cambia, solo cambias aquí.
         */
        public static Response from(ProductEntity product) {
            return Response.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .description(product.getDescription())
                    .price(product.getPrice())
                    .stockQuantity(product.getStockQuantity())
                    .category(product.getCategory())
                    .active(product.isActive())
                    .build();
        }
    }

    /**
     * CreateRequest: cuerpo esperado al crear un producto (POST /products).
     * Las anotaciones de Bean Validation se verifican con @Valid en el controller.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        private String name;

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        private String description;

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
        @Digits(integer = 8, fraction = 2, message = "Precio inválido")
        private BigDecimal price;

        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        private Integer stockQuantity;

        @NotBlank(message = "La categoría es obligatoria")
        private String category;
    }

    /**
     * StockUpdateRequest: body para actualizar solo el stock (PATCH /products/{id}/stock).
     * Separar este DTO evita que el cliente envíe campos no deseados.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockUpdateRequest {

        @NotNull(message = "La cantidad es obligatoria")
        private Integer quantity; // puede ser negativo (reducir stock)
    }
}

package com.e_commerce.orderservice.DTOs;


import com.e_commerce.orderservice.Entity.OrderEntity;
import com.e_commerce.orderservice.Entity.OrderItem;
import com.e_commerce.orderservice.Enums.EStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs de la API de Órdenes.
 */
public class OrderDTO {

    /**
     * Request para crear una nueva orden.
     * El cliente envía este JSON en POST /orders.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "El ID del cliente es obligatorio")
        private Long customerId;

        @Email
        @NotNull(message = "El Email es Obligatorio")
        private String customerEmail;

        @NotEmpty(message = "La orden debe tener al menos un producto")
        @Valid  // valida recursivamente cada elemento de la lista
        private List<OrderItemRequest> items;

        /** Dirección de entrega opcional */
        private String deliveryAddress;
    }

    /**
     * Cada línea de producto dentro de la orden.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "El ID del producto es obligatorio")
        @Positive(message = "El ID del producto debe ser positivo")
        private Long productId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 100, message = "La cantidad máxima por producto es 100")
        private Integer quantity;
    }

    /**
     * Response: lo que devolvemos al cliente después de crear/consultar una orden.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long customerId;
        private String userEmail;
        private EStatus status;
        private List<OrderItemResponse> items;
        private BigDecimal totalAmount;
        private String deliveryAddress;
        private LocalDateTime createdAt;
        private String message; // mensaje informativo al cliente

        /**
         * Convierte la entidad Order al DTO de respuesta.
         * Incluye los items con su información (producto + cantidad + subtotal).
         */
        public static Response from(OrderEntity order) {
            return Response.builder()
                    .id(order.getId())
                    .customerId(order.getCustomerId())
                    .userEmail(order.getUserEmail())
                    .status(order.getStatus())
                    .items(order.getItems() != null
                            ? order.getItems().stream().map(OrderItemResponse::from).toList()
                            : List.of())
                    .totalAmount(order.getTotalAmount())
                    .deliveryAddress(order.getDeliveryAddress())
                    .createdAt(order.getCreatedAt())
                    .build();
        }
    }

    /**
     * Cada línea del detalle de la orden en la respuesta.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal; // unitPrice × quantity

        public static OrderItemResponse from(OrderItem item) {
            return OrderItemResponse.builder()
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build();
        }
    }
}
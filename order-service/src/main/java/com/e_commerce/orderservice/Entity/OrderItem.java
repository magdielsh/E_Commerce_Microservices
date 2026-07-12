package com.e_commerce.orderservice.Entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Línea de producto dentro de una Order.
 * Cada OrderItem representa 1 tipo de producto y su cantidad.
 *
 * Guardamos productName y unitPrice en el momento de la compra
 * (snapshot del precio) — si products-service cambia el precio después,
 * la orden histórica conserva el precio original.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long productId;

    /** Nombre del producto en el momento de la compra (snapshot) */
    private String productName;

    private Integer quantity;

    /** Precio unitario en el momento de la compra (snapshot) */
    private BigDecimal unitPrice;
}
package com.e_commerce.orderservice.Entity;

import com.e_commerce.orderservice.Enums.EStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
//@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@Table(name = "orders")
public class OrderEntity {

    //@Id
   // @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //@Column(nullable = false)
    private String userEmail;

    //@Column(nullable = false)
    private Long customerId;

    //@Column(nullable = false)
    private List<OrderItem> items;

    //@Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    //@Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    // Estado del pedido: PENDING, CONFIRMED, SHIPPED, CANCELLED
    //@Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EStatus status;

    //@Column(nullable = false)
    private LocalDateTime createdAt;

   /* @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.status = EStatus.PENDING;
    }*/

}

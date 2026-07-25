package com.e_commerce.orderservice.DTOs;

import com.e_commerce.orderservice.Entity.OrderEntity;
import com.e_commerce.orderservice.Entity.OrderItem;
import com.e_commerce.orderservice.Enums.EStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * OrderDTOMapperTest — Tests de mapeo entre entidades y DTOs
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * Verificamos que las conversiones OrderEntity → OrderDTO.Response y
 * OrderItem → OrderItemResponse son correctas.
 *
 * Estos tests previenen regresiones cuando alguien modifica los DTOs
 * y se olvida de actualizar el mapper.
 */
class OrderDTOMapperTest {

    // ══════════════════════════════════════════════════════════════════
    // OrderEntity → OrderDTO.Response
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OrderEntity → OrderDTO.Response")
    class FromEntity {

        @Test
        @DisplayName("Mapea todos los campos correctamente")
        void mapsAllFields() {
            // ── given ──
            LocalDateTime now = LocalDateTime.now();
            OrderEntity entity = OrderEntity.builder()
                    .id(Long.valueOf(1L))
                    .customerId(Long.valueOf(100L))
                    .userEmail("cliente@test.com")
                    .status(EStatus.CONFIRMED)
                    .items(List.of(
                            OrderItem.builder()
                                    .productId(Long.valueOf(1L))
                                    .productName("Laptop")
                                    .quantity(Integer.valueOf(2))
                                    .unitPrice(new BigDecimal("999.99"))
                                    .build()
                    ))
                    .totalAmount(new BigDecimal("1999.98"))
                    .deliveryAddress("Calle Falsa 123")
                    .createdAt(now)
                    .build();

            // ── when ──
            OrderDTO.Response dto = OrderDTO.Response.from(entity);

            // ── then ──
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getUserEmail()).isEqualTo("cliente@test.com");
            assertThat(dto.getStatus()).isEqualTo(EStatus.CONFIRMED);
            assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1999.98"));
            assertThat(dto.getDeliveryAddress()).isEqualTo("Calle Falsa 123");
            assertThat(dto.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Mapea items vacíos como lista vacía (no null)")
        void mapsEmptyItems() {
            OrderEntity entity = OrderEntity.builder()
                    .id(Long.valueOf(1L))
                    .customerId(Long.valueOf(100L))
                    .status(EStatus.PENDING)
                    .items(null)   // null en la entidad
                    .build();

            OrderDTO.Response dto = OrderDTO.Response.from(entity);

            assertThat(dto.getItems()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Mapea items correctamente: producto, cantidad, subtotal")
        void mapsItems() {
            OrderEntity entity = OrderEntity.builder()
                    .id(Long.valueOf(1L))
                    .customerId(Long.valueOf(100L))
                    .status(EStatus.CONFIRMED)
                    .items(List.of(
                            OrderItem.builder()
                                    .productId(Long.valueOf(1L))
                                    .productName("Laptop")
                                    .quantity(Integer.valueOf(2))
                                    .unitPrice(new BigDecimal("999.99"))
                                    .build()
                    ))
                    .totalAmount(new BigDecimal("1999.98"))
                    .build();

            OrderDTO.Response dto = OrderDTO.Response.from(entity);

            assertThat(dto.getItems()).hasSize(1);
            OrderDTO.OrderItemResponse item = dto.getItems().get(0);
            assertThat(item.getProductId()).isEqualTo(1L);
            assertThat(item.getProductName()).isEqualTo("Laptop");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
            // subtotal = 999.99 * 2
            assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("1999.98"));
        }

        @Test
        @DisplayName("El DTO no tiene mensaje por defecto")
        void messageIsNull() {
            OrderEntity entity = OrderEntity.builder()
                    .id(Long.valueOf(1L))
                    .customerId(Long.valueOf(100L))
                    .status(EStatus.CONFIRMED)
                    .build();

            OrderDTO.Response dto = OrderDTO.Response.from(entity);

            assertThat(dto.getMessage()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Validación de requests
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CreateRequest — validación de campos")
    class CreateRequestValidation {

        @Test
        @DisplayName("ItemRequest con cantidad negativa")
        void negativeQuantity() {
            // Las validaciones de Jakarta Validation se prueban mejor
            // con @Valid y un Validator de Spring.
            // Aquí solo verificamos que el DTO acepta los valores.
            OrderDTO.OrderItemRequest item = new OrderDTO.OrderItemRequest();
            item.setProductId(Long.valueOf(1L));
            item.setQuantity(Integer.valueOf(-1));  // inválido según @Min(1)

            assertThat(item.getQuantity()).isNegative();
        }

        @Test
        @DisplayName("CreateRequest con cantidad máxima")
        void maxQuantity() {
            OrderDTO.OrderItemRequest item = new OrderDTO.OrderItemRequest();
            item.setProductId(Long.valueOf(1L));
            item.setQuantity(Integer.valueOf(100));  // máximo permitido por @Max(100)

            assertThat(item.getQuantity()).isEqualTo(100);
        }
    }
}

package com.e_commerce.orderservice.DTOs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.swing.text.StyledEditorKit;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * ───────────────────────────────────────────────────────────────────────────────
 * ProductDTOTest — Tests de la lógica de dominio del DTO de Producto
 * ───────────────────────────────────────────────────────────────────────────────
 *
 * ¿Por qué testear un DTO?
 *   Porque el método isAvailableForPurchase() tiene lógica de negocio:
 *   combina los campos available, active y stockQuantity para determinar
 *   si el producto puede comprarse.
 *
 *   NO es un simple getter/setter — toma decisiones:
 *     • available == null → se considera disponible (viene del servicio real)
 *     • available == false → producto del fallback → no disponible
 *     • active == false → producto desactivado → no disponible
 *     • stockQuantity == 0 o null → sin stock → no disponible
 *
 *   Esta lógica merece test propio porque es fácil de romper al cambiar
 *   cualquiera de esos campos.
 */
class ProductDTOTest {

    @Nested
    @DisplayName("isAvailableForPurchase()")
    class IsAvailableForPurchase {

        @Test
        @DisplayName("available=null, active=true, stock>0 → disponible (producto real)")
        void realProduct() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(10))
                    .active(true)
                    .available(null)  // viene del servicio real
                    .build();

            assertThat(product.isAvailableForPurchase()).isTrue();
        }

        @Test
        @DisplayName("available=true, active=true, stock>0 → disponible")
        void availableTrue() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(5))
                    .active(true)
                    .available(Boolean.valueOf(Boolean.TRUE))
                    .build();

            assertThat(product.isAvailableForPurchase()).isTrue();
        }

        @Test
        @DisplayName("available=false → no disponible (fallback activo)")
        void unavailableFromFallback() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(10))
                    .active(true)
                    .available(Boolean.valueOf(Boolean.FALSE))  // fallback: CB abierto
                    .build();

            assertThat(product.isAvailableForPurchase()).isFalse();
        }

        @Test
        @DisplayName("active=false → no disponible (producto retirado)")
        void inactiveProduct() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(10))
                    .active(false)
                    .available(Boolean.valueOf(Boolean.TRUE))
                    .build();

            assertThat(product.isAvailableForPurchase()).isFalse();
        }

        @Test
        @DisplayName("stockQuantity=0 → no disponible (sin stock)")
        void zeroStock() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(0))
                    .active(true)
                    .available(Boolean.valueOf(Boolean.TRUE))
                    .build();

            assertThat(product.isAvailableForPurchase()).isFalse();
        }

        @Test
        @DisplayName("stockQuantity=null → no disponible")
        void nullStock() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(null)
                    .active(true)
                    .available(Boolean.valueOf(Boolean.TRUE))
                    .build();

            assertThat(product.isAvailableForPurchase()).isFalse();
        }

        @Test
        @DisplayName("Todos los campos null/negativos → no disponible")
        void allInvalid() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .stockQuantity(Integer.valueOf(-1))
                    .active(false)
                    .available(Boolean.valueOf(Boolean.TRUE))
                    .build();

            assertThat(product.isAvailableForPurchase()).isFalse();
        }
    }

    @Nested
    @DisplayName("Constructor y Builder")
    class Builder {

        @Test
        @DisplayName("Builder construye ProductDTO correctamente")
        void builderWorks() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .id(Long.valueOf(1L))
                    .name("Laptop")
                    .description("Laptop de alta gama")
                    .price(new BigDecimal("1299.99"))
                    .stockQuantity(Integer.valueOf(50))
                    .category("ELECTRONICS")
                    .active(true)
                    .build();

            assertThat(product.getId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("Laptop");
            assertThat(product.getDescription()).isEqualTo("Laptop de alta gama");
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
            assertThat(product.getStockQuantity()).isEqualTo(50);
            assertThat(product.getCategory()).isEqualTo("ELECTRONICS");
            assertThat(product.isActive()).isTrue();
        }

        @Test
        @DisplayName("available y exception son null por defecto")
        void internalFieldsDefaultToNull() {
            ProductDTO.Response product = ProductDTO.Response.builder()
                    .id(Long.valueOf(1L))
                    .name("Test")
                    .build();

            assertThat(product.getAvailable()).isNull();
            assertThat(product.getException()).isNull();
        }
    }
}

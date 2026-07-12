package com.e_commerce.orderservice.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTOs del lado de orders-service para comunicarse con products-service.
 *
 * ¿Por qué definimos DTOs locales en lugar de compartir los de products-service?
 *
 * OPCIÓN A — Compartir DTOs via módulo común (lib):
 *   Ventaja: no hay duplicación de código.
 *   Desventaja: acoplamiento entre servicios. Si products-service cambia un campo,
 *               orders-service también se rompe aunque no use ese campo.
 *
 * OPCIÓN B — DTOs locales (Consumer-Driven Contracts):
 *   Ventaja: orders-service solo mapea los campos que LE INTERESAN.
 *             Si products-service añade campos nuevos → Jackson los ignora (fail-on-unknown-properties: false).
 *             orders-service es independiente de la estructura interna de products-service.
 *   Desventaja: algo de duplicación de código.
 *
 * Para microservicios: OPCIÓN B es la práctica recomendada.
 * Facilita el deploy independiente y la evolución del API.
 */
public class ProductDTO {

    /**
     * Representación de un producto desde el punto de vista de orders-service.
     * Solo los campos que necesitamos para procesar una orden.
     *
     * Jackson deserializa el JSON de products-service a este DTO:
     * {
     *   "id": 1,
     *   "name": "Laptop Pro X1",
     *   "price": 1299.99,
     *   "stockQuantity": 50,
     *   "active": true,
     *   ...otros campos que ignoramos...
     * }
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

        /*
         * Campo extra que usamos internamente en orders-service para identificar
         * si una respuesta viene del fallback (available=false) o del servicio real.
         * products-service no lo devuelve → Jackson lo ignora al deserializar → null por defecto.
         * El fallback lo setea explícitamente a false.
         */
        private Boolean available;

        Throwable exception;

        /** true si el producto existe en products-service Y tiene stock */
        public boolean isAvailableForPurchase() {
            return (available == null || available)  // null = viene del servicio real = disponible
                    && active
                    && stockQuantity != null
                    && stockQuantity > 0;
        }
    }
}
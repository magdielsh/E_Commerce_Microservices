package com.e_commerce.orderservice.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entidad de dominio: representa un Producto en memoria, no vamos a persistir en BD ya que el objetivo es ver FEING
 *
 * En un proyecto real usarías @Entity + JPA con una DB real.
 * Aquí usamos un Map en memoria para mantener el foco en Feign.
 *
 * Anotaciones Lombok (evitan escribir boilerplate):
 *   @Data          → genera getters, setters, equals, hashCode, toString
 *   @Builder       → patrón Builder: Product.builder().id(1L).name("X").build()
 *   @NoArgsConstructor → constructor vacío (requerido por Jackson para deserializar)
 *   @AllArgsConstructor → constructor con todos los campos (requerido por @Builder)
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {

    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    /** Unidades disponibles en almacén */
    private Integer stockQuantity;

    /** Categoría del producto: ELECTRONICS, CLOTHING, FOOD, etc. */
    private String category;

    /** true = visible y vendible; false = retirado del catálogo */
    private boolean active;
}

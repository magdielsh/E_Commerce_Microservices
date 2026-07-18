package com.e_commerce.productservice.Service;

import com.e_commerce.productservice.DTOs.ProductDTO;
import com.e_commerce.productservice.Entity.ProductEntity;
import com.e_commerce.productservice.Exceptions.ProductExceptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Capa de servicio (lógica de negocio) del módulo de Productos.
 *
 * @Service: marca este Bean como componente de la capa de servicio.
 * Spring lo instancia como Singleton (una sola instancia por contexto).
 * <p>
 * Usamos ConcurrentHashMap como "base de datos" en memoria:
 * - Thread-safe: múltiples hilos pueden leer/escribir sin race conditions
 * - En producción: reemplazar por JpaRepository + DB real (PostgreSQL, MySQL, etc.)
 */
@Slf4j
@Service
public class ProductService {

    /*
     * ConcurrentHashMap<Long, Product>:
     *   - Long    = ID del producto (la clave primaria)
     *   - Product = la entidad completa (el valor)
     *
     * ConcurrentHashMap vs HashMap:
     *   - HashMap NO es thread-safe → en un servidor con múltiples threads,
     *     dos requests simultáneas podrían corromper el mapa.
     *   - ConcurrentHashMap usa bloqueos por segmento → thread-safe sin bloquear
     *     toda la estructura, como haría Collections.synchronizedMap(new HashMap()).
     */
    private final Map<Long, ProductEntity> productStore = new ConcurrentHashMap<>();

    /*
     * AtomicLong: contador thread-safe para generar IDs únicos.
     * atomicLong.incrementAndGet() es una operación atómica:
     *   - Lee el valor actual
     *   - Lo incrementa en 1
     *   - Devuelve el nuevo valor
     * Todo en una sola operación no interrumpible → nunca genera IDs duplicados.
     */
    private final AtomicLong idSequence = new AtomicLong(0);

    /**
     * @PostConstruct: se ejecuta UNA vez, justo después de que Spring
     * inyecta todas las dependencias del Bean.
     * Úsalo para inicializar datos, conexiones, o validar configuración.
     * En producción: esto vendría de un DataLoader o migration (Flyway/Liquibase).
     */
    @PostConstruct
    public void initData() {
        log.info("Inicializando datos de productos...");

        // Creamos 4 productos de ejemplo con datos realistas
        ProductEntity product1 = ProductEntity.builder()
                .id(idSequence.incrementAndGet())    // ID = 1
                .name("Laptop Pro X1")
                .description("Laptop de alto rendimiento con 16GB RAM y SSD 512GB")
                .price(new BigDecimal("1299.99"))
                .stockQuantity(50)
                .category("ELECTRONICS")
                .active(true)
                .build();
        productStore.put(product1.getId(), product1);

        ProductEntity product2 = ProductEntity.builder()
                .id(idSequence.incrementAndGet())    // ID = 2
                .name("Auriculares Bluetooth Z500")
                .description("Auriculares inalámbricos con cancelación de ruido activa")
                .price(new BigDecimal("249.99"))
                .stockQuantity(120)
                .category("ELECTRONICS")
                .active(true)
                .build();
        productStore.put(product2.getId(), product2);

        ProductEntity product3 = ProductEntity.builder()
                .id(idSequence.incrementAndGet())    // ID = 3
                .name("Camiseta Running Pro")
                .description("Camiseta técnica transpirable para deporte de alto rendimiento")
                .price(new BigDecimal("39.99"))
                .stockQuantity(200)
                .category("CLOTHING")
                .active(true)
                .build();
        productStore.put(product3.getId(), product3);

        ProductEntity product4 = ProductEntity.builder()
                .id(idSequence.incrementAndGet())    // ID = 4
                .name("Proteína Whey Premium")
                .description("Proteína de suero 2kg, sabor chocolate, 25g proteína/ración")
                .price(new BigDecimal("59.99"))
                .stockQuantity(0)     // ← sin stock para probar ese escenario
                .category("SUPPLEMENTS")
                .active(true)
                .build();
        productStore.put(product4.getId(), product4);

        log.info("Productos inicializados: {}", productStore.size());
    }

    // ─── CRUD ──────────────────────────────────────────────────────

    /**
     * Obtiene todos los productos activos.
     * Si se filtra por categoría, devuelve solo los de esa categoría.
     */
    public List<ProductDTO.Response> findAll(String category) {
        return productStore.values()
                .stream()
                // Si category es null o vacío, devuelve todos los activos
                // Si se especifica categoría, filtra por ella (case-insensitive)
                .filter(p -> p.isActive() &&
                        (category == null || category.isBlank() ||
                                p.getCategory().equalsIgnoreCase(category)))
                .map(ProductDTO.Response::from)  // convierte Product → ProductDTO.Response
                .toList();
    }

    /**
     * Busca un producto por ID.
     * Lanza ProductNotFoundException si no existe → GlobalExceptionHandler la captura
     * y devuelve 404, que Feign recibirá en orders-service.
     */
    public ProductDTO.Response findById(Long id) {
        log.info("Buscando producto ID={}", id);

        ProductEntity product = productStore.get(id);

        if (product == null) {
            // Esta excepción sale del servicio → GlobalExceptionHandler → 404 JSON
            // Feign en orders-service recibe el 404 → ErrorDecoder → excepción de dominio
            throw new ProductExceptions.ProductNotFoundException(id);
        }

        return ProductDTO.Response.from(product);
    }

    /**
     * Crea un nuevo producto.
     * Devuelve el DTO del producto creado (con su nuevo ID).
     */
    public ProductDTO.Response create(ProductDTO.CreateRequest request) {
        log.info("Creando producto: name={}", request.getName());

        // Verificar nombre duplicado
        boolean exists = productStore.values().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(request.getName()));
        if (exists) {
            throw new ProductExceptions.ProductAlreadyExistsException(request.getName());
        }

        long newId = idSequence.incrementAndGet();

        ProductEntity product = ProductEntity.builder()
                .id(newId)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory())
                .active(true)  // por defecto activo
                .build();

        save(product);
        log.info("Producto creado con ID={}", newId);

        return ProductDTO.Response.from(product);
    }

    /**
     * Actualiza el stock de un producto.
     * quantity puede ser:
     * - Positivo: se añade stock (reposición)
     * - Negativo: se reduce stock (venta/reserva)
     * <p>
     * Este método lo llama orders-service cuando confirma una orden.
     */
    public ProductDTO.Response updateStock(Long productId, int quantity) {
        log.info("Actualizando el stock: productId={}, quantity={}", productId, quantity);

        // findById lanza 404 si no existe
        ProductEntity product = productStore.get(productId);
        if (product == null) {
            throw new ProductExceptions.ProductNotFoundException(productId);
        }

        int newStock = product.getStockQuantity() + quantity;

        // Validar que no quedemos con stock negativo
        if (newStock < 0) {
            throw new ProductExceptions.InsufficientStockException(
                    productId,
                    product.getStockQuantity(),
                    Math.abs(quantity)
            );
        }

        // Actualizar el stock
        product.setStockQuantity(newStock);
        save(product);

        log.info("Stock actualizado: productId={}, stockAnterior={}, stockNuevo={}",
                productId, newStock - quantity, newStock);

        return ProductDTO.Response.from(product);
    }

    // ─── Método interno ────────────────────────────────────────────

    /**
     * Persiste el producto en el mapa (simula el save() de JpaRepository)
     */
    private void save(ProductEntity product) {
        productStore.put(product.getId(), product);
    }

    /**
     * Devuelve todos los productos para testing (sin filtros)
     */
    public List<ProductDTO.Response> findAllRaw() {
        return new ArrayList<>(productStore.values())
                .stream()
                .map(ProductDTO.Response::from)
                .toList();
    }
}
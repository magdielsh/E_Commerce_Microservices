package com.e_commerce.productservice.Controller;


import com.e_commerce.productservice.DTOs.ProductDTO;
import com.e_commerce.productservice.Service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST del microservicio de Productos.
 *
 * @RestController: combina @Controller + @ResponseBody.
 *   - @Controller: marca la clase como manejador de peticiones HTTP.
 *   - @ResponseBody: serializa automáticamente el return value a JSON
 *                    (usando Jackson, que Spring Boot configura automáticamente).
 *
 * @RequestMapping("/products"): prefijo base para todos los endpoints.
 *   Todos los métodos de esta clase responden bajo /products/...
 *
 * @RequiredArgsConstructor (Lombok): genera un constructor con todos los campos
 *   final → Spring lo usa para inyección por constructor (mejor práctica vs @Autowired).
 *
 * @Slf4j (Lombok): inyecta 'private static final Logger log = ...'
 *
 * ENDPOINTS EXPUESTOS (consumidos por orders-service vía Feign):
 *   GET    /products           → todos los productos (con filtro opcional)
 *   GET    /products/{id}      → producto por ID
 *   POST   /products           → crear producto
 *   PATCH  /products/{id}/stock → actualizar stock
 */
@Slf4j
@RestController
@RequestMapping("v1/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /products
     * GET /products?category=ELECTRONICS
     *
     * @RequestParam(required = false): el parámetro es opcional.
     *   Si no se envía: category = null → se devuelven todos los activos.
     *   Si se envía: se filtra por categoría.
     *
     * ResponseEntity<List<...>>: envuelve la respuesta con control del HTTP status code.
     * .ok(body): 200 OK con el body serializado a JSON.
     */
    @GetMapping
    public ResponseEntity<List<ProductDTO.Response>> getAllProducts(
            @RequestParam(required = false) String category) {

        log.info("GET /products - category={}", category);

        List<ProductDTO.Response> products = productService.findAll(category);
        return ResponseEntity.ok(products);
    }

    /**
     * GET /products/{id}
     *
     * @PathVariable("id"): extrae el {id} de la URL y lo mapea al parámetro Long id.
     *
     * Casos:
     *   - Producto existe → 200 OK + body JSON
     *   - Producto NO existe → ProductService lanza ProductNotFoundException
     *                        → GlobalExceptionHandler devuelve 404 JSON
     *                        → Feign en orders-service recibe 404
     *                        → ErrorDecoder lo convierte en excepción de dominio
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO.Response> getProductById(@PathVariable("id") Long id) {
        log.info("GET /products/{}", id);

        ProductDTO.Response product = productService.findById(id);
        return ResponseEntity.ok(product);
    }

    /**
     * POST /products
     * Body: JSON con CreateRequest
     *
     * @Valid: activa las validaciones de Bean Validation en el DTO.
     *         Si alguna falla → MethodArgumentNotValidException
     *         → GlobalExceptionHandler → 400 BAD REQUEST con lista de errores.
     *
     * @RequestBody: deserializa el JSON del body a CreateRequest (vía Jackson).
     *
     * @ResponseStatus(HttpStatus.CREATED): podríamos usar esto, pero preferimos
     *         ResponseEntity para ser explícitos → 201 CREATED.
     */
    @PostMapping
    public ResponseEntity<ProductDTO.Response> createProduct(
            @Valid @RequestBody ProductDTO.CreateRequest request) {

        log.info("POST /products - name={}", request.getName());

        ProductDTO.Response created = productService.create(request);

        // 201 CREATED: indica que el recurso fue creado exitosamente.
        // En producción también agregarías el header Location: /products/{id}
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PATCH /products/{id}/stock
     * Body: { "quantity": -2 } para reducir, { "quantity": 10 } para añadir
     *
     * Usamos PATCH (no PUT) porque solo actualizamos UN campo (stock),
     * no el recurso completo. PUT reemplaza TODO el recurso.
     *
     * Este endpoint lo llama orders-service cuando confirma una orden:
     *   productClient.updateStock(productId, new StockUpdateRequest(-quantity))
     *
     * Casos:
     *   - Stock suficiente → 200 OK + producto actualizado
     *   - Stock insuficiente → InsufficientStockException → 409 CONFLICT
     */
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductDTO.Response> updateProductStock(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductDTO.StockUpdateRequest request) {

        log.info("PATCH /products/{}/stock - quantity={}", id, request.getQuantity());

        ProductDTO.Response updated = productService.updateStock(id, request.getQuantity());
        return ResponseEntity.ok(updated);
    }

    /**
     * GET /products/health-check
     *
     * Endpoint extra para que orders-service verifique si products-service está vivo.
     * En producción, Actuator ya provee /actuator/health para esto,
     * pero este endpoint personalizado demuestra cómo Feign puede consultarlo.
     */
    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("products-service is UP");
    }
}
package com.e_commerce.orderservice.Service;

import com.e_commerce.orderservice.DTOs.ProductDTO;
import com.e_commerce.orderservice.Feing.ProductClient;
import com.e_commerce.orderservice.Feing.ProductClientFallbackFactory;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderServiceFeing {

    private final ProductClient productClient;

    private final ProductClientFallbackFactory productClientFallbackFactory;

    @Retry(name = "products-service")
    @CircuitBreaker(name = "products-service")
    @Bulkhead(name = "productsBulkhead", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackgetProductById")
    public ProductDTO.Response getProductById(Long id) {
        return productClient.getProductById(id);
    }

    @Retry(name = "products-service")
    @CircuitBreaker(name = "products-service")
    @Bulkhead(name = "productsBulkhead", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackupdateProductStock")
    public ProductDTO.Response updateProductStock(Long productId, ProductClient.StockUpdateRequest request) {
        return productClient.updateProductStock(productId, request);
    }

    // --- FALLBACKS INDEPENDIENTES REUTILIZANDO EL FACTORY ---
    public ProductDTO.Response fallbackgetProductById(Long id, Throwable t) {
        ProductClient clientFallback = productClientFallbackFactory.create(t);
        return clientFallback.getProductById(id);
    }

    public ProductDTO.Response fallbackupdateProductStock(Long productId, ProductClient.StockUpdateRequest request, Throwable t) {
        ProductClient clientFallback = productClientFallbackFactory.create(t);
        return clientFallback.updateProductStock(productId, request);
    }
}

package com.e_commerce.reviewsservice.Controller;

import com.e_commerce.reviewsservice.Dto.CreateReviewRequestDTO;
import com.e_commerce.reviewsservice.Dto.ReviewResponseDTO;
import com.e_commerce.reviewsservice.Service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponseDTO> createReview(@Valid @RequestBody CreateReviewRequestDTO request) {
        ReviewResponseDTO created = reviewService.createReview(request);
        // 201 Created, no 200: estamos creando un recurso nuevo.
        // En un proyecto real aquí también armarías el header "Location"
        // apuntando a GET /api/v1/reviews/{id}, pero lo simplificamos por ahora.
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<ReviewResponseDTO>> getReviewsByProduct(
            @PathVariable String productId,
            // @PageableDefault evita que un cliente sin parámetros te pida
            // "todas las reseñas" por accidente -> tamaño de página forzado.
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsByProduct(productId, pageable));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponseDTO> getReviewById(@PathVariable String reviewId) {
        return ResponseEntity.ok(reviewService.getReviewById(reviewId));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponseDTO> updateReview(
            @PathVariable String reviewId,
            @RequestParam Integer rating,
            @RequestParam String comment) {
        return ResponseEntity.ok(reviewService.updateReview(reviewId, rating, comment));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable String reviewId) {
        reviewService.deleteReview(reviewId);
        // 204 No Content: la operación fue exitosa, no hay body que regresar.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/can-review")
    public ResponseEntity<Boolean> canUserReview(
            @RequestParam String productId,
            @RequestParam String userId) {
        return ResponseEntity.ok(reviewService.canUserReview(productId, userId));
    }
}

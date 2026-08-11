package com.e_commerce.reviewsservice.Service;

import com.e_commerce.reviewsservice.Dto.CreateReviewRequestDTO;
import com.e_commerce.reviewsservice.Dto.ReviewResponseDTO;
import com.e_commerce.reviewsservice.Entity.Review;
import com.e_commerce.reviewsservice.Entity.ReviewPhoto;
import com.e_commerce.reviewsservice.Exception.DuplicateReviewException;
import com.e_commerce.reviewsservice.Exception.ReviewConflictException;
import com.e_commerce.reviewsservice.Exception.ReviewNotFoundException;
import com.e_commerce.reviewsservice.Repository.ReviewRepository;
import com.mongodb.MongoWriteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewResponseDTO createReview(CreateReviewRequestDTO request) {

        List<ReviewPhoto> photos = request.getPhotoUrls() == null
                ? List.of()
                : request.getPhotoUrls().stream()
                .map(url -> ReviewPhoto.builder().url(url).build())
                .collect(Collectors.toList());

        Review review = Review.builder()
                .productId(request.getProductId())
                .userId(request.getUserId())
                .rating(request.getRating())
                .comment(request.getComment())
                .photos(photos)
                .build();

        try {
            Review saved = reviewRepository.save(review);
            log.info("Reseña creada: productId={}, userId={}, reviewId={}",
                    request.getProductId(), request.getUserId(), saved.getId());
            return ReviewResponseDTO.from(saved);

        } catch (DuplicateKeyException ex) {
            // Aquí es donde el índice único {productId, userId} que definimos
            // en el @Document se convierte en una excepción real de Mongo.
            // La atrapamos aquí -en el Service- y la traducimos a nuestra
            // excepción de dominio, para que el Controller y el cliente de la API
            // nunca sepan que por debajo existe un índice de MongoDB.
            log.info("Intento de reseña duplicada: productId={}, userId={}",
                    request.getProductId(), request.getUserId());
            throw new DuplicateReviewException(request.getProductId(), request.getUserId());
        }
    }

    // Paginado: NUNCA regresamos List<Review> completo de un producto popular
    // con miles de reseñas. Pageable ya lo conoces de Spring Data JPA,
    // funciona idéntico aquí.
    public Page<ReviewResponseDTO> getReviewsByProduct(String productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(ReviewResponseDTO::from);
    }

    public ReviewResponseDTO getReviewById(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        return ReviewResponseDTO.from(review);
    }

    // Actualizar el comentario/rating de una reseña existente.
    // Aquí SÍ nos importa el @Version: si dos requests llegan casi al mismo tiempo
    // (el usuario edita dos veces desde dos pestañas, por ejemplo), el segundo
    // save() con una versión desactualizada lanza OptimisticLockingFailureException.
    public ReviewResponseDTO updateReview(String reviewId, Integer newRating, String newComment) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        review.setRating(newRating);
        review.setComment(newComment);

        try {
            Review updated = reviewRepository.save(review);
            return ReviewResponseDTO.from(updated);
        } catch (OptimisticLockingFailureException ex) {
            log.info("Conflicto de versión al actualizar reviewId={}", reviewId);
            throw new ReviewConflictException(reviewId);
        }
    }

    public void deleteReview(String reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ReviewNotFoundException(reviewId);
        }
        reviewRepository.deleteById(reviewId);
    }

    // Regla de negocio expuesta como método propio, útil por ejemplo si
    // order-service quiere validar "¿puede este usuario reseñar este producto?"
    // antes de mostrar el formulario en el frontend.
    public boolean canUserReview(String productId, String userId) {
        return !reviewRepository.existsByProductIdAndUserId(productId, userId);
    }
}

package com.e_commerce.reviewsservice.Exception;

public class ReviewConflictException extends RuntimeException {
    public ReviewConflictException(String reviewId) {
        super("La reseña " + reviewId + " fue modificada por otra operación. Intenta de nuevo.");
    }
}

package com.e_commerce.reviewsservice.Exception;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(String reviewId) {
        super("No se encontró la reseña con id: " + reviewId);
    }
}

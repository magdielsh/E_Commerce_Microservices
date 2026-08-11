package com.e_commerce.reviewsservice.Exception;

public class DuplicateReviewException extends RuntimeException {
    public DuplicateReviewException(String productId, String userId) {
        super("El usuario " + userId + " ya publicó una reseña para el producto " + productId);
    }
}

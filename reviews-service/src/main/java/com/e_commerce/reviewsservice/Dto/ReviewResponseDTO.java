package com.e_commerce.reviewsservice.Dto;

import com.e_commerce.reviewsservice.Entity.Review;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
public class ReviewResponseDTO {

    String id;
    String productId;
    String userId;
    Integer rating;
    String comment;
    List<String> photoUrls;
    boolean hasSellerResponse;
    Instant createdAt;

    public static ReviewResponseDTO from(Review review) {
        List<String> urls = review.getPhotos() == null
                ? List.of()
                : review.getPhotos().stream().map(p -> p.getUrl()).toList();

        return new ReviewResponseDTO(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                review.getRating(),
                review.getComment(),
                urls,
                review.getSellerResponse() != null,
                review.getCreatedAt()
        );
    }

}



package com.e_commerce.reviewsservice.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateReviewRequestDTO {

    @NotBlank
    String productId;

    @NotBlank
    String userId;

    @Min(1)
    @Max(5)
    Integer rating;

    @Size(max = 2000)
    String comment;

    List<String> photoUrls;
}

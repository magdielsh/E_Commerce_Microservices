package com.e_commerce.reviewsservice.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "reviews", collation = "en")
@CompoundIndexes({
        @CompoundIndex(name = "product_created_idx", def = "{'productId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "unique_user_product_idx", def = "{'productId': 1, 'userId': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    private String id;

    @Indexed
    @Field("product_id")
    private String productId;

    @Indexed
    @Field("user_id")
    private String userId;

    private Integer rating;

    private String comment;

    private List<ReviewPhoto> photos;

    private SellerResponse sellerResponse;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("update_at")
    private Instant updateAt;

    @Version
    private Long version;

}

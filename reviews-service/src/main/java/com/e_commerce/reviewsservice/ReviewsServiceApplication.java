package com.e_commerce.reviewsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class ReviewsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewsServiceApplication.class, args);
	}

}

package com.e_commerce.userservice.Repository;

import com.e_commerce.userservice.Entity.UserEntity;
import com.e_commerce.userservice.Entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    void deleteByUser(UserEntity user);
}

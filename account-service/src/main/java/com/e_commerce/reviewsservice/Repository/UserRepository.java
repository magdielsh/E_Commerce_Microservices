package com.e_commerce.reviewsservice.Repository;


import com.e_commerce.reviewsservice.Entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "5000")}) // Espera máx. 5 segundos
    Optional<UserEntity> findByEmail(String email);
}

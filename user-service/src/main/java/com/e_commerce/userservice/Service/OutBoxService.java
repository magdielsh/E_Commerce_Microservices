package com.e_commerce.userservice.Service;

import com.e_commerce.userservice.Dto.UserCreatedValidationEvent;
import com.e_commerce.userservice.Entity.OutboxEvent;
import com.e_commerce.userservice.Repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutBoxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

//    public OutboxEvent buildOutboxEvent(UserCreatedValidationEvent userCVE) {
//
//            return outboxEventRepository.save(OutboxEvent.create(
//                    "User",
//                    userCVE.userId().toString(),
//                    UserCreatedValidationEvent.class.getName(),
//                    userCVE,
//                    userCVE.userId().toString()
//            ));
//
//    }

}

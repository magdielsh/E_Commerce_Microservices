package com.e_commerce.reviewsservice.Exceptions;

public class EmailAlreadyExistsException extends BusinessException{

    public EmailAlreadyExistsException(String email) {
        super("El email ya está registrado: " + email);
    }
}

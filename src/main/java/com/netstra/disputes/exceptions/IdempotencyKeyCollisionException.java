package com.netstra.disputes.exceptions;

public class IdempotencyKeyCollisionException extends RuntimeException{
    private String message;

    public IdempotencyKeyCollisionException(String message){
        super(message);
    }
}

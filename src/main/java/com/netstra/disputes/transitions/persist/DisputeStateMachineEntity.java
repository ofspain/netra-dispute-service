package com.netstra.disputes.transitions.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DisputeStateMachineEntity {


    private String id; // category:disputeId


    private byte[] stateMachineContext;


    private LocalDateTime lastUpdated = LocalDateTime.now();
}

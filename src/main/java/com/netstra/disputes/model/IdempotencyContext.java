package com.netstra.disputes.model;

import com.netra.commons.requests.CreateDisputeRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class IdempotencyContext {
    // 📨 Incoming request data (ephemeral)
    private String key;           // From UI/API request
    private String actor;         // Who initiated
    private IdemOperation operation;     // What operation
    private String fingerprint;   // Hash of request payload
    private boolean replay;       // Computed flag
    private LocalDateTime createdAt;


    public static String generateCanonicalFingerPrint(CreateDisputeRequest request) {
        String issuer = request.getAffectedAccount().getIssuingInstitution().getCode();
        String initiatorID = String.valueOf(request.getInitiator().getId());
        String initiatorType = request.getInitiator().getDisputantType().name();


        String canonical = String.join("|",
                initiatorID,
                initiatorType,
                request.getDisputeMode().name(),
                request.getTransactionDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                String.valueOf(request.getTransactionAmount()),
                request.getTransactionRail().getInstrument().name(),
                request.getTransactionRail().getPaymentRail().name(),
                request.getTransactionAction().name(),
                issuer
        );

        return canonical;
    }

    public enum IdemOperation{
        CREATE_DISPIUTE
    }
}

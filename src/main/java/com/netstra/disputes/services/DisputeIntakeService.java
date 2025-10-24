package com.netstra.disputes.services;

import com.netra.commons.requests.CreateDisputeRequest;
import com.netstra.disputes.security.DomainAwarePrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisputeIntakeService {


    private final DisputeValidatorService disputeValidatorService;

    public Object createDispute(CreateDisputeRequest request, DomainAwarePrincipal user, boolean skipStructuralValidation){
        if(!skipStructuralValidation){
            disputeValidatorService.structuralValidationOfDisputeRequest(request, user);
        }
        disputeValidatorService.businessAndDomainLevelValidation(request,user);
        return null;
    }

    public void issuerEnrichment(){

    }
}

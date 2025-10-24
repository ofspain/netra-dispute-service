package com.netstra.disputes.services;

import com.netra.commons.enums.TransactionInstrument;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.TransactionType;
import com.netra.commons.models.rules.*;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netra.commons.validators.models.RulesEvaluationResult;
import com.netstra.disputes.security.DomainAwarePrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DisputeValidatorService {

    private final Validator validator;

    //todo: autowire all necessary rules repository
    public RulesEvaluationResult evaluateDisputeForProcessing(Dispute dispute){
        DisputeRuleAggregator aggregatedRule = null; //load a rule applicable to this dispute

        List<DisputeStateTransitionRule> stateTransitionRules = aggregatedRule.getDisputeStateTransitionRules();
        List<DisputeTemporalRule> temporalRules = aggregatedRule.getTemporalRules();
        List<DisputeAccessRule> accessRules = aggregatedRule.getAccessRules();
        List<DisputeDecisionRule> decisionRules = aggregatedRule.getDecisionRules();

        Set<TransactionInstrument> instruments = aggregatedRule.getTransactionInstruments();
        TransactionType transactionType = aggregatedRule.getTransactionType();

        //todo: validate this dispute can actually use this rule first based on instrument and transType
        return null;
    }

    public void structuralValidationOfDisputeRequest(CreateDisputeRequest request, DomainAwarePrincipal user) {

            var violations = validator.validate(request);

            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder("Validation failed: ");
                for (ConstraintViolation<?> v : violations) {
                    sb.append(String.format("[%s: %s] ", v.getPropertyPath(), v.getMessage()));
                }
                throw new IllegalArgumentException(sb.toString());
            }
    }

    public void businessAndDomainLevelValidation(CreateDisputeRequest disputeRequest, DomainAwarePrincipal user){

    }


}

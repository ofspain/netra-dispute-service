package com.netstra.disputes.services;

import com.netra.commons.enums.TransactionInstrument;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.TransactionType;
import com.netra.commons.models.rules.*;
import com.netra.commons.validators.models.RulesEvaluationResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class DisputeValidatorService {
    //autowire all necessary rules repository
    public RulesEvaluationResult evaluateDisputeForProcessing(Dispute dispute){
        DisputeRuleAggregator aggregatedRule = null; //load a rule applicable to this dispute

        List<DisputeStateTransitionRule> stateTransitionRules = aggregatedRule.getDisputeStateTransitionRules();
        List<DisputeTemporalRule> temporalRules = aggregatedRule.getTemporalRules();
        List<DisputeAccessRule> accessRules = aggregatedRule.getAccessRules();
        List<DisputeDecisionRule> decisionRules = aggregatedRule.getDecisionRules();

        Set<TransactionInstrument> instruments = aggregatedRule.getTransactionInstruments();
        TransactionType transactionType = aggregatedRule.getTransactionType();

        //todo: validate this dispute can actually use this rule first
        return null;
    }
}

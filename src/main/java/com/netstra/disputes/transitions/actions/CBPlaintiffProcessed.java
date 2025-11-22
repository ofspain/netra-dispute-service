package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.Evidence;
import com.netstra.disputes.model.IdempotencyContext;
import com.netstra.disputes.model.ProcessedEvidenceDTO;
import com.netstra.disputes.services.EvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Qualifier("cbPlaintiffProcessedAC")
@RequiredArgsConstructor
public class CBPlaintiffProcessed implements DisputeStateMachineAction{
    private final EvidenceService evidenceService;

    @Override
    public DisputeTransitionEvent trigger() {
        return DisputeTransitionEvent.EVENT_PLAINTIFF_PROCESSED;
    }

    @Override
    public DisputeMode mode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {

        DisputeState state = context.getTarget().getId();
        IdempotencyContext idempotencyContext = context.getExtendedState().get("idempotencyContext", IdempotencyContext.class);
        Dispute dispute =  context.getExtendedState().get("dispute", Dispute.class); // ← ADD THIS


        switch (state){
            case PLAINTIFF_DECLINED -> {


            }
            case PLAINTIFF_VERIFIED -> {

            }
            case AWAITING_MANUAL_EVIDENCE_REVIEW -> {

            }

        }
        dispute.setCurrentState(state);


    }
}

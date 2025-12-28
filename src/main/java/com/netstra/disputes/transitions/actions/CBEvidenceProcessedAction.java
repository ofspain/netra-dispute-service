package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.Evidence;
import com.netstra.disputes.model.ProcessedEvidenceDTO;
import com.netstra.disputes.services.EvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("cbEvidenceProcessedAC") // Add explicit name here
//@Qualifier("cbEvidenceProcessedAC")
@RequiredArgsConstructor
public class CBEvidenceProcessedAction implements DisputeStateMachineAction {

    private final EvidenceService evidenceService;

    @Override
    public DisputeTransitionEvent trigger() {
        return DisputeTransitionEvent.EVENT_EVIDENCE_PROCESSED;
    }

    @Override
    public DisputeMode mode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {

        DisputeState toState = context.getTarget().getId();
        Dispute dispute =  context.getExtendedState().get("dispute", Dispute.class); // ← ADD THIS


        switch (toState){
            case EVIDENCE_VERIFIED -> {
                List<ProcessedEvidenceDTO> verifiedEvidences = context.getExtendedState().get("verifiedEvidences", List.class);
                for(ProcessedEvidenceDTO processedEvidenceDTO : verifiedEvidences){
                    Evidence evidence = new Evidence();
                    evidence.setId(processedEvidenceDTO.getEvidenceId());
                    evidence.setPerpetualHash(processedEvidenceDTO.getPHash());
                    evidenceService.updateEvidence(evidence, processedEvidenceDTO.getEvidenceId());
                }

            }
            case EVIDENCE_REJECTED -> {

            }
            case AWAITING_MANUAL_EVIDENCE_REVIEW -> {

            }

        }
        dispute.setCurrentState(toState);


    }
}


package com.netstra.disputes.transitions.guards;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.services.DisputeService;
import com.netstra.disputes.transitions.guards.bootstrap.DisputeBootstrapGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvidenceRequiredGuard implements DisputeBootstrapGuard {

    private final DisputeService disputeService;

    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {
        Message<?> msg = context.getMessage();
        if (msg == null) return false;

        Long disputeId = (Long) msg.getHeaders().get("disputeId");
        String currentUser = (String) msg.getHeaders().get("currentUser");//logged in user
        String disputant = (String) msg.getHeaders().get("disputant");

        if (disputeId == null || currentUser == null) return false;

        //use disputant to validate that this is going to awaiting_evidence_validation

        // Example business rule
        return false; //disputeService.isUserAllowedToSubmitEvidence(disputeId, currentUser);
    }

    @Override
    public DisputeState getTargetState(){
        return DisputeState.AWAITING_EVIDENCE_VERIFICATION;
    }

    @Override
    public DisputeTransitionEvent getTrigger() {
        return DisputeTransitionEvent.BOOTSTRAP_CONTEXT_USER;
    }


}

package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EvidenceAction implements DisputeStateMachineAction {

    @Override
    public DisputeState getTargetState() {
        return DisputeState.AWAITING_EVIDENCE_VERIFICATION;
    }

    @Override
    public void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {
        Message<?> msg = context.getMessage();
        if (msg == null) return;

        Map<Object, Object> vars = context.getExtendedState().getVariables();
        vars.put("disputeId", msg.getHeaders().get("disputeId"));
        vars.put("currentUser", msg.getHeaders().get("currentUser"));

        System.out.println("Evidence context in ExtendedState: " + vars);

        //consider this for more statelessness
        //Dispute dispute = (Dispute) context.getMessageHeader("dispute");
        //        System.out.println("Initiating auto-review for dispute " + dispute.getId());
        //
    }
}


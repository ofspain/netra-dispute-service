package com.netstra.disputes.transitions.listener;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.models.Dispute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.OnTransitionStart;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.event.TransitionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@WithStateMachine(name = "chargebackStateMachineFactoryConfig")
@Slf4j
public class ChargebackStateListener {

    @OnTransition(source = "EVIDENCE_PENDING", target = "EVIDENCE_VERIFIED")
    public void handleEvidenceVerified(StateContext<DisputeState, TransitionEvent> ctx) {
        String disputeId = ctx.getMessageHeader("disputeId").toString();
        String actor = ctx.getMessageHeader("actor").toString();
        Boolean aiConfirmed = (Boolean) ctx.getMessageHeader("aiConfirmed");

        log.info("Evidence verified for dispute {}, actor={}, aiConfirmed={}", disputeId, actor, aiConfirmed);

        // Example: enrich domain object still in memory
        Dispute dispute = (Dispute) ctx.getExtendedState().getVariables().get("dispute");
        if (dispute != null) {
            dispute.setDisputeMarkedLegitTime(LocalDateTime.now());
            dispute.setNote("Evidence auto-confirmed by AI.");
        }

        // You could signal back via event bus or nominal service return
        ctx.getExtendedState().getVariables().put("postTransitionResult", Map.of(
                "status", "SUCCESS",
                "disputeId", disputeId,
                "timestamp", LocalDateTime.now()
        ));
    }

    @OnTransitionStart(source = "EVIDENCE_PENDING", target = "EVIDENCE_VERIFIED")
    public void preCheck(StateContext<DisputeState, TransitionEvent> ctx) {
        log.debug("Pre-check for transition: {}", ctx.getTransition().getTrigger().getEvent());
    }

}


// postTransitionResult is now available in the service layer

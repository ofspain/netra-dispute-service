package com.netstra.disputes.transitions.listener;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.Evidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.OnTransitionStart;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.event.TransitionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


/**
 * Per-Transition Listener
 *
 * For highly-contextual actions like:
 *
 * Notify actor
 *
 * Push to Kafka
 *
 * Blockchain anchoring
 *
 * Modify enriched domain object
 *
 * Feed ML confirmation back into timeline
 */

@Component
@WithStateMachine(name = "chargebackStateMachineFactoryConfig")
@Slf4j
@RequiredArgsConstructor
public class ChargebackStateListener {


    private final KafkaTemplate<String, List<Evidence>> kafkaTemplate;


    @OnTransition(source = "EVIDENCE_PENDING", target = "EVIDENCE_VERIFIED")
    public void handleEvidenceVerified(StateContext<DisputeState, TransitionEvent> ctx) {

        //todo: reanalyse, let do side effect actions here such as notification, publish to kafka, and say push to aptos

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

    @OnTransitionStart(source = "BOOTSTRAP_DISPUTE_CONTEXT", target = "AWAITING_EVIDENCE_VERIFICATION")
    public void preCheck(StateContext<DisputeState, TransitionEvent> ctx) {
        log.debug("transition: {}", ctx.getTransition().getTrigger().getEvent());

//todo: consider sending only s3 key and dispute id        // Push to Kafka for downstream (Python) analysis
        List<Evidence> evidences = ctx.getExtendedState().get("evidences", List.class);

        kafkaTemplate.send("evidence-analysis-topic", evidences);

        //todo: send notification here too

    }

}


//        StateContext<DisputeState, DisputeTransitionEvent> context = transition.getStateContext();
//        IdempotencyContext idemCtx = extractIdempotencyContext(context);
//
//        log.warn("Transition denied: {} -> {} for dispute: {}",
//                transition.getSource().getId(),
//                transition.getTarget().getId(),
//                idemCtx.getDisputeId());
//
//        // Record metrics
//        metricsService.incrementCounter("statemachine.transition.denied",
//                "source", transition.getSource().getId().name(),
//                "target", transition.getTarget().getId().name(),
//                "event", transition.getTrigger().getEvent().name(),
//                "reason", "GUARD_REJECTION");
//
//        // Notify interested parties
//        notificationService.notifyTransitionRejection(
//                idemCtx,
//                transition.getSource().getId(),
//                transition.getTarget().getId(),
//                transition.getTrigger().getEvent()
//        );

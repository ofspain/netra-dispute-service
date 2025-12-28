package com.netstra.disputes.transitions.service;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.util.BasicUtil;
import com.netstra.disputes.idempotency.IdempotencyContext;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.services.imaging.ImageValidationResult;
import com.netstra.disputes.transitions.config.StateMachineRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeStateMachineService {

    private final StateMachineRegistry machineRegistry;


    public StateTransitionResult sendEvent(Dispute dispute,
                                           DisputeTransitionEvent event,
                                           Map<String, Object> headers,
                                           DisputeState intendedState,
                                           DomainAwarePrincipal user) {
        return sendEvent(dispute, event, headers, intendedState, user, null);
    }

    public StateTransitionResult sendEvent(Dispute dispute,
                                           DisputeTransitionEvent event,
                                           Map<String, Object> headers,
                                           DisputeState intendedState,
                                           DomainAwarePrincipal user,
                                           List<ImageValidationResult> evidences) {

        StateMachine<DisputeState, DisputeTransitionEvent> sm =
                getStateMachine(dispute, user,  evidences, intendedState);

        DisputeState initial = sm.getState().getId();

        Message<DisputeTransitionEvent> message = buildMessage(dispute, event, headers);

        List<StateMachineEventResult<DisputeState, DisputeTransitionEvent>> results =
                sm.sendEventCollect(Mono.just(message)).block(Duration.ofSeconds(30));

        boolean accepted = wasAccepted(results);
        DisputeState finalState = sm.getState().getId();

        return buildResult(dispute, event, initial, finalState, accepted);
        //stateMachine1
        //	.sendEvent(Mono.just(MessageBuilder
        //		.withPayload("E1").build()))
        //	.blockLast();
    }

    // -------- Internal helpers --------
    private StateMachine<DisputeState, DisputeTransitionEvent> getStateMachine(
            Dispute dispute,
            DomainAwarePrincipal user,
            List<ImageValidationResult> evidences,
            DisputeState intended) {

        StateMachine<DisputeState, DisputeTransitionEvent> sm =
                machineRegistry.getStateMachineFactory(dispute.getDisputeMode())
                        .getStateMachine(dispute.getId().toString());

        populateExtendedState(sm, dispute, user,  evidences, intended);
        return sm;
    }

    private void populateExtendedState(StateMachine<DisputeState, DisputeTransitionEvent> sm,
                                       Dispute dispute,
                                       DomainAwarePrincipal user,
                                       List<ImageValidationResult> evidences,
                                       DisputeState intendedState) {

        Map<Object, Object> vars = sm.getExtendedState().getVariables();

        vars.put("dispute", dispute);
        vars.put("user", user);
        vars.put("intendedState", intendedState);

        if (BasicUtil.validList(evidences)) {
            vars.put("validEvidences", evidences);
        }
    }

    private Message<DisputeTransitionEvent> buildMessage(
            Dispute dispute,
            DisputeTransitionEvent event,
            Map<String, Object> headers) {

        MessageBuilder<DisputeTransitionEvent> mb = MessageBuilder.withPayload(event)
                .setHeader("disputeId", dispute.getId().toString())
                .setHeader("dispute", dispute)
                .setHeader("timestamp", LocalDateTime.now());

        if (headers != null) headers.forEach(mb::setHeader);
        return mb.build();
    }

    private boolean wasAccepted(
            List<StateMachineEventResult<DisputeState, DisputeTransitionEvent>> results) {

        return results != null &&
                results.stream().anyMatch(r -> r.getResultType() ==
                        StateMachineEventResult.ResultType.ACCEPTED);
    }

    private StateTransitionResult buildResult(
            Dispute dispute,
            DisputeTransitionEvent event,
            DisputeState prev,
            DisputeState next,
            boolean accepted) {

        return StateTransitionResult.builder()
                .accepted(accepted)
                .stateChanged(!prev.equals(next))
                .previousState(prev)
                .newState(next)
                .event(event)
                .dispute(dispute)
                .build();
    }
}

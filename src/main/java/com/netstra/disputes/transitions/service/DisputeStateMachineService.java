package com.netstra.disputes.transitions.service;

import com.netra.commons.enums.DisputantType;
import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netra.commons.util.BasicUtil;
import com.netstra.disputes.model.IdempotencyContext;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.transitions.config.StateMachineRegistry;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
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
                                           DomainAwarePrincipal user,
                                           IdempotencyContext idCtx) {
        return sendEvent(dispute, event, headers, intendedState, user, idCtx, null);
    }

    public StateTransitionResult sendEvent(Dispute dispute,
                                           DisputeTransitionEvent event,
                                           Map<String, Object> headers,
                                           DisputeState intendedState,
                                           DomainAwarePrincipal user,
                                           IdempotencyContext idCtx,
                                           List<String> evidences) {

        StateMachine<DisputeState, DisputeTransitionEvent> sm =
                getStateMachine(dispute, user, idCtx, evidences, intendedState);

        DisputeState initial = sm.getState().getId();

        Message<DisputeTransitionEvent> message = buildMessage(dispute, event, headers);

        List<StateMachineEventResult<DisputeState, DisputeTransitionEvent>> results =
                sm.sendEventCollect(Mono.just(message)).block(Duration.ofSeconds(30));

        boolean accepted = wasAccepted(results);
        DisputeState finalState = sm.getState().getId();

        return buildResult(dispute, event, initial, finalState, accepted);
    }

    // -------- Internal helpers --------
    private StateMachine<DisputeState, DisputeTransitionEvent> getStateMachine(
            Dispute dispute,
            DomainAwarePrincipal user,
            IdempotencyContext idemCtx,
            List<String> evidences,
            DisputeState intended) {

        StateMachine<DisputeState, DisputeTransitionEvent> sm =
                machineRegistry.getStateMachineFactory(dispute.getDisputeMode())
                        .getStateMachine(dispute.getId().toString());

        populateExtendedState(sm, dispute, user, idemCtx, evidences, intended);
        return sm;
    }

    private void populateExtendedState(StateMachine<DisputeState, DisputeTransitionEvent> sm,
                                       Dispute dispute,
                                       DomainAwarePrincipal user,
                                       IdempotencyContext idemCtx,
                                       List<String> evidences,
                                       DisputeState intendedState) {

        Map<Object, Object> vars = sm.getExtendedState().getVariables();

        vars.put("dispute", dispute);
        vars.put("user", user);
        vars.put("idempotencyContext", idemCtx);
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

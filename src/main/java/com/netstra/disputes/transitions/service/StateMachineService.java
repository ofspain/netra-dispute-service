package com.netstra.disputes.transitions.service;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netstra.disputes.services.DisputeService;
import com.netstra.disputes.transitions.config.StateMachineRegistry;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
@RequiredArgsConstructor
public class StateMachineService {

    private final StateMachineRegistry machineRegistry;
    private final DisputeStateMachinePersistService persistService;
    private final DisputeService disputeService;


    public StateMachine<DisputeState, DisputeTransitionEvent> getStateMachineForDispute(Long disputeId, DisputeMode mode) {
        // Get the dispute entity
        Dispute dispute = disputeService.findById(Long.valueOf(disputeId))
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        return getStateMachineForDispute(dispute, mode);
    }

    public StateMachine<DisputeState, DisputeTransitionEvent> getStateMachineForDispute(Dispute dispute, DisputeMode mode) {
        // Get the factory for the specific mode
        StateMachineFactory<DisputeState, DisputeTransitionEvent> factory =
                machineRegistry.getStateMachineFactory(mode);

        // Create state machine with dispute ID as machine ID
        StateMachine<DisputeState, DisputeTransitionEvent> stateMachine =
                factory.getStateMachine(dispute.getId().toString());

        // Restore the state machine context from persistence
        restoreStateMachine(stateMachine, dispute);

        return stateMachine;
    }

    private void restoreStateMachine(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine, Dispute dispute) {
        try {
            // Stop the state machine before restoring
            stateMachine.stop();

            // Restore from persistence
            StateMachineContext<DisputeState, DisputeTransitionEvent> context =
                    persistService.read(dispute.getId().toString());

            if (context != null) {
                // Restore the persisted context
                stateMachine.getStateMachineAccessor()
                        .doWithAllRegions(access -> {
                            access.resetStateMachine(context);
                        });
            } else {
                // Initialize with dispute's current state if no persisted context
                stateMachine.getStateMachineAccessor()
                        .doWithAllRegions(access -> {
                            access.resetStateMachine(new DefaultStateMachineContext<>(
                                    dispute.getCurrentState(), null, null, null, null, stateMachine.getId()));
                        });
            }

            // Start the state machine
            stateMachine.start();

        } catch (Exception e) {
            throw new RuntimeException("Failed to restore state machine for dispute: " + dispute.getId(), e);
        }
    }

    public StateTransitionResult processEvent(Long disputeId, DisputeMode mode, DisputeTransitionEvent event) {
        return processEvent(disputeId, mode, event, null);
    }

    public StateTransitionResult processEvent(Long disputeId, DisputeMode mode,
                                              DisputeTransitionEvent event, Map<String, Object> headers) {
        Dispute dispute = disputeService.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        StateMachine<DisputeState, DisputeTransitionEvent> stateMachine =
                getStateMachineForDispute(dispute, mode);

        // Build message with headers
        MessageBuilder<DisputeTransitionEvent> messageBuilder = MessageBuilder.withPayload(event);

        if (headers != null) {
            headers.forEach(messageBuilder::setHeader);
        }

        // Always include the dispute entity and ID
        messageBuilder.setHeader("dispute", dispute);
        messageBuilder.setHeader("disputeId", dispute.getId().toString());

        Message<DisputeTransitionEvent> message = messageBuilder.build();

        // Store initial state
        DisputeState initialState = stateMachine.getState().getId();

        // Send event to state machine
        boolean accepted = stateMachine.sendEvent(message);

        // Get final state
        DisputeState finalState = stateMachine.getState().getId();

        // Persist the updated state machine context
        try {
            persistService.persist(stateMachine, dispute.getId().toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist state machine after event processing", e);
        }

        return StateTransitionResult.builder()
                .accepted(accepted)
                .stateChanged(!initialState.equals(finalState))
                .previousState(initialState)
                .newState(finalState)
                .event(event)
                .dispute(dispute)
                .build();
    }

    // Helper method to check if transition is possible without executing it
    public boolean canProcessEvent(Long disputeId, DisputeMode mode, DisputeTransitionEvent event) {
        Dispute dispute = disputeService.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        StateMachine<DisputeState, DisputeTransitionEvent> stateMachine =
                getStateMachineForDispute(dispute, mode);

        return stateMachine.getTransitions().stream()
                .filter(transition -> transition.getSource().getId().equals(stateMachine.getState().getId()))
                .anyMatch(transition -> transition.getTrigger() != null &&
                        transition.getTrigger().getEvent().equals(event));
    }
}
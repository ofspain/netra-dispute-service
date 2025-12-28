package com.netstra.disputes.transitions.persist;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.transition.TransitionKind;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeStateMachinePersistenceInterceptor
        extends StateMachineInterceptorAdapter<DisputeState, DisputeTransitionEvent> {

    @Override
    public Message<DisputeTransitionEvent> preEvent(Message<DisputeTransitionEvent> message,
                                                    StateMachine<DisputeState, DisputeTransitionEvent> stateMachine) {
        log.info("BEFORE EVENT {}, {}", message.getPayload(), stateMachine.getState());
        return message;
    }

    @Override
    public void preStateChange(State<DisputeState, DisputeTransitionEvent> state,
                               Message<DisputeTransitionEvent> message,
                               Transition<DisputeState, DisputeTransitionEvent> transition,
                               StateMachine<DisputeState, DisputeTransitionEvent> stateMachine,
                               StateMachine<DisputeState, DisputeTransitionEvent> rootStateMachine) {
        log.info("Just before state change {}->{}, {}", transition.getSource(), transition.getTarget(), transition.getTrigger());

        // You can add pre-state change persistence logic here if needed
//        if (message != null && stateMachine != null && stateMachine.getId() != null) {
//            String disputeId = stateMachine.getId();
//            try {
//                // Persist before state change if needed
//                StateMachineContext<DisputeState, DisputeTransitionEvent> context =
//                        new DefaultStateMachineContext<>(
//                                state != null ? state.getId() : stateMachine.getState().getId(),
//                                message.getPayload(),
//                                message.getHeaders(),
//                                stateMachine.getExtendedState(),
//                                null,
//                                disputeId
//                        );
//                persistService.write(context, disputeId);
//            } catch (Exception e) {
//                System.err.println("Failed to persist state machine in preStateChange: " + e.getMessage());
//            }
//        }
    }

    @Override
    public void postStateChange(State<DisputeState, DisputeTransitionEvent> state,
                                Message<DisputeTransitionEvent> message,
                                Transition<DisputeState, DisputeTransitionEvent> transition,
                                StateMachine<DisputeState, DisputeTransitionEvent> stateMachine,
                                StateMachine<DisputeState, DisputeTransitionEvent> rootStateMachine) {
        log.info("Just after state change {}->{}, {}", transition.getSource(), transition.getTarget(), transition.getTrigger());


//        // Auto-persist after successful state change
//        if (stateMachine != null && stateMachine.getId() != null) {
//            String disputeId = stateMachine.getId();
//            try {
//                StateMachineContext<DisputeState, DisputeTransitionEvent> context =
//                        new DefaultStateMachineContext<>(
//                                stateMachine.getState().getId(),
//                                message != null ? message.getPayload() : null,
//                                message != null ? message.getHeaders() : null,
//                                stateMachine.getExtendedState(),
//                                null,
//                                disputeId
//                        );
//                persistService.write(context, disputeId);
//
//                System.out.println("Auto-persisted state machine for dispute: " + disputeId +
//                        ", state: " + stateMachine.getState().getId());
//            } catch (Exception e) {
//                System.err.println("Failed to auto-persist state machine for dispute " + disputeId +
//                        ": " + e.getMessage());
//            }
//        }
    }

    @Override
    public StateContext<DisputeState, DisputeTransitionEvent> preTransition(
            StateContext<DisputeState, DisputeTransitionEvent> stateContext) {
        log.info("Just before transition");

        return stateContext;
    }

    @Override
    public StateContext<DisputeState, DisputeTransitionEvent> postTransition(
            StateContext<DisputeState, DisputeTransitionEvent> stateContext) {
        log.info("Just after transition");

        // Handle internal transitions
//        if (stateContext.getTransition() != null &&
//                stateContext.getTransition().getKind() == TransitionKind.INTERNAL &&
//                stateContext.getStateMachine() != null &&
//                stateContext.getStateMachine().getId() != null) {
//
//            String disputeId = stateContext.getStateMachine().getId();
//            try {
//                StateMachineContext<DisputeState, DisputeTransitionEvent> context =
//                        new DefaultStateMachineContext<>(
//                                stateContext.getStateMachine().getState().getId(),
//                                stateContext.getEvent(),
//                                stateContext.getMessageHeaders(),
//                                stateContext.getStateMachine().getExtendedState(),
//                                null,
//                                disputeId
//                        );
//                persistService.write(context, disputeId);
//            } catch (Exception e) {
//                System.err.println("Failed to persist state machine after internal transition: " + e.getMessage());
//            }
//        }

        return stateContext;
    }


}

package com.netstra.disputes.transitions.listener;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GlobalStateMachineListener implements StateMachineListener<DisputeState, DisputeTransitionEvent> {

   /**Global Listener
    Metrics
    Logging
    Audit
    Global side-effects
    Error capture
    **/

    @Override
    public void stateChanged(State<DisputeState, DisputeTransitionEvent> from, State<DisputeState, DisputeTransitionEvent> to) {
        log.info("Global Listener → State changed from [{}] to [{}]",
                from == null ? "none" : from.getId(),
                to.getId());
    }

    @Override
    public void stateEntered(State<DisputeState, DisputeTransitionEvent> state) {

    }

    @Override
    public void stateExited(State<DisputeState, DisputeTransitionEvent> state) {

    }

    @Override
    public void eventNotAccepted(Message<DisputeTransitionEvent> event) {
        log.warn("Event [{}] was not accepted. DisputeId=[{}]",
                event.getPayload(),
                event.getHeaders().get("disputeId"));
    }

    @Override
    public void transition(Transition<DisputeState, DisputeTransitionEvent> transition) {

    }

    @Override
    public void transitionStarted(Transition<DisputeState, DisputeTransitionEvent> transition) {

    }

    @Override
    public void transitionEnded(Transition<DisputeState, DisputeTransitionEvent> transition) {

    }

    @Override
    public void stateMachineStarted(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine) {

    }

    @Override
    public void stateMachineStopped(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine) {

    }

    @Override
    public void stateMachineError(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine,
                                  Exception exception) {
        log.error("Error in machine [{}]: {}", stateMachine.getId(), exception.getMessage(), exception);

    }

    @Override
    public void extendedStateChanged(Object key, Object value) {

    }

    @Override
    public void stateContext(StateContext<DisputeState, DisputeTransitionEvent> stateContext) {

    }
}

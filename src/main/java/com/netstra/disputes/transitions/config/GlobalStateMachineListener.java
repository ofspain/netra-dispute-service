package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GlobalStateMachineListener extends StateMachineListenerAdapter<DisputeState, DisputeTransitionEvent> {


    @Override
    public void stateChanged(State<DisputeState, DisputeTransitionEvent> from, State<DisputeState, DisputeTransitionEvent> to) {
        log.info("Global Listener → State changed from [{}] to [{}]",
                from == null ? "none" : from.getId(),
                to.getId());
    }

    @Override
    public void eventNotAccepted(Message<DisputeTransitionEvent> event) {
        log.warn("Event [{}] was not accepted. DisputeId=[{}]",
                event.getPayload(),
                event.getHeaders().get("disputeId"));
    }

    @Override
    public void stateMachineError(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine, Exception exception) {
        log.error("Error in machine [{}]: {}", stateMachine.getId(), exception.getMessage(), exception);
    }
}


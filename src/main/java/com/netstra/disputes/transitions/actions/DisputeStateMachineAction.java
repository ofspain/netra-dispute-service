package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.statemachine.action.Action;

public interface DisputeStateMachineAction extends Action<DisputeState, DisputeTransitionEvent> {
    DisputeState getTargetState();
}

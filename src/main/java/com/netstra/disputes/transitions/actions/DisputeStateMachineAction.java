package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

public interface DisputeStateMachineAction extends Action<DisputeState, DisputeTransitionEvent> {

    DisputeTransitionEvent trigger();
    DisputeMode mode();
}


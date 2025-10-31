package com.netstra.disputes.transitions.bootstrap.action;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.DisputeBootstrapComponent;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

public interface DisputeBootstrapAction
        extends Action<DisputeState, DisputeTransitionEvent>, DisputeBootstrapComponent {
    void perform(StateContext<DisputeState, DisputeTransitionEvent> context);

    @Override
    default void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {
        perform(context);
    }
}

package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

@Component
@Qualifier("cbWithdrawnAC")
@RequiredArgsConstructor
public class CBWithdrawnAction  implements Action<DisputeState, DisputeTransitionEvent> {

    @Override
    public void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {
        DisputeState sourceState = context.getSource().getId();
        DisputeTransitionEvent sourceEvent = context.getEvent();

        //todo: use source event to decipher actual destination state, or calling service inject actual destination to
        // extended state, as guard has been passed to get here, so we can trust service injected intended state
    }
}

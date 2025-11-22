package com.netstra.disputes.transitions.guards;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.model.ProcessedEvidences;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

@Component
@Qualifier("cbEvidenceProcessedGD")
@RequiredArgsConstructor
public class CBEvidenceProcessedGuard  implements Guard<DisputeState, DisputeTransitionEvent> {
    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {
        DisputeState actualState = context.getTarget().getId();
        DisputeState intendedState = context.getExtendedState().get("intendedState", DisputeState.class);


        //for now until otherwise
        return actualState.equals(intendedState);
    }
}

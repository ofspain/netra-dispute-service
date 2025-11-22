package com.netstra.disputes.transitions.guards;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.util.BasicUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Qualifier("cbWithdrawnGD")
@RequiredArgsConstructor
public class CBWithdrawnGuard  implements Guard<DisputeState, DisputeTransitionEvent> {
    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {
        DisputeState intendedState = context.getExtendedState().get("intendedState", DisputeState.class);
        DisputeTransitionEvent sourceEvent = context.getEvent();

        Map<DisputeTransitionEvent, DisputeState> withdrawalRoutingMap = BasicUtil.getWithdrawalRoutingMap();

        return null != intendedState && intendedState.equals(withdrawalRoutingMap.get(sourceEvent));
    }
}

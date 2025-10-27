package com.netstra.disputes.transitions.guards;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GFGuardAwaitIssVerification implements Guard<DisputeState, DisputeTransitionEvent> {

    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {
        DisputeState source = context.getSource().getId();
        DisputeState target = context.getTarget().getId();
        DisputeTransitionEvent event = context.getEvent();

        log.info("Evaluating transition: {} → {} on event {}", source, target, event);

        //do individual logic here
        switch (target){
            case ISSUER_VERIFIED -> {

            }
            case AWAITING_ISSUER_VERIFICATION_UNREACHABLE -> {

            }
            case ISSUER_DECLINED -> {

            }
            case WITHDRAWN_CUSTOMER, WITHDRAWN_SUB_INSTITUTION, WITHDRAWN_ACQUIRER -> {

            }
            case EXPIRED -> {

            }
        }

        //do common logic here
        return false;
    }

}

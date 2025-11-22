package com.netstra.disputes.transitions.bootstrap.guard;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.BaseUser;
import com.netra.commons.models.CustomerUser;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CHBBootstrapGuard implements Guard<DisputeState, DisputeTransitionEvent> {
    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {
        DisputeState actualState = context.getTarget().getId();
        DisputeState intendedState = context.getExtendedState().get("intendedState", DisputeState.class);

        if(actualState.equals(intendedState)){


            List<String> validEvidences =  context.getExtendedState().get("validEvidences", List.class);
            BaseUser user = context.getExtendedState().get("user", BaseUser.class);
            //can still use instanceof to lock down disputant to some type: eg institution or customer user

            boolean userValid = user.isEnabled();
            boolean evidenceValid = true;

            if(user instanceof CustomerUser){
                evidenceValid = null != validEvidences && !validEvidences.isEmpty();
            }




            return evidenceValid && userValid;

        }

        return false;
    }
}

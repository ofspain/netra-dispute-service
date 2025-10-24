package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;

@Configuration
@EnableStateMachineFactory(name="goodfaithStateMachineFactory")
public class GoodFaithStateMachineFactoryConfig
        extends EnumStateMachineConfigurerAdapter<DisputeState, DisputeTransitionEvent>   implements StateMachineContract{

    @Override
    public DisputeMode stateMachineMode() {
        return DisputeMode.GOODFAITH;
    }
}

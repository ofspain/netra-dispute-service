package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.actions.DisputeStateMachineAction;
import com.netstra.disputes.transitions.guards.DisputeBootstrapGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

import java.util.*;

@Configuration
@EnableStateMachineFactory(name="chargebackStateMachineFactory")
@RequiredArgsConstructor
public class ChargebackStateMachineFactoryConfig
        extends EnumStateMachineConfigurerAdapter<DisputeState, DisputeTransitionEvent>  implements StateMachineContract{

    private final StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> persister;


    private final GlobalStateMachineListener globalListener;

    //todo: initiate this with required guards and actions
    private final List<DisputeBootstrapGuard> bootstrapGuards = new ArrayList<>();
    private final Map<DisputeState, DisputeStateMachineAction> bootstrapActions = new HashMap<>();

    @Override
    public DisputeMode stateMachineMode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public void configure(StateMachineStateConfigurer<DisputeState, DisputeTransitionEvent> states) throws Exception {
        states.withStates()
                .initial(DisputeState.BOOTSTRAPING_DISPUTE_CONTEXT)
                .states(EnumSet.allOf(DisputeState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<DisputeState, DisputeTransitionEvent> transitions) throws Exception {
        for (DisputeBootstrapGuard guard : bootstrapGuards) {
            DisputeState target = guard.getTargetState();
            transitions
                    .withExternal()
                    .source(DisputeState.BOOTSTRAPING_DISPUTE_CONTEXT)
                    .target(target)
                    .event(guard.getTrigger())
                    .guard(guard)
                    .action(findActionForTarget(target))
                    .and();
        }
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<DisputeState, DisputeTransitionEvent> config)
            throws Exception {
        config
                .withConfiguration()
                .autoStartup(false)
                .listener(globalListener)
                .and()
                .withPersistence()
                .runtimePersister(persister);
    }


    //todo: use app specific exception
    public DisputeStateMachineAction findActionForTarget(DisputeState target){
        return Optional.ofNullable(bootstrapActions.get(target))
                .orElseThrow(() -> new IllegalArgumentException("No bootstrap action found for target: " + target));

    }

}

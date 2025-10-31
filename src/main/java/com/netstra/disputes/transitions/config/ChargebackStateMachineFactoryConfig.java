package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.BootstrapLifecycleRegistry;
import com.netstra.disputes.transitions.bootstrap.BootstrapTransition;
import com.netstra.disputes.transitions.bootstrap.action.DisputeBootstrapAction;
import com.netstra.disputes.transitions.bootstrap.guard.DisputeBootstrapGuard;
import com.netstra.disputes.transitions.util.ChargebackTransitionRegistry;
import com.netstra.disputes.transitions.util.StateMachineWiringEngine;
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

    private final BootstrapLifecycleRegistry bootstrapRegistry;
    private final ChargebackTransitionRegistry otherTransitionRegistry;
    private final StateMachineWiringEngine wiringEngine;


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
        Collection<BootstrapTransition> chbkBootstrapTransition = bootstrapRegistry.getTransitions(stateMachineMode());

        for (BootstrapTransition transition : chbkBootstrapTransition) {
            DisputeBootstrapGuard guard = transition.getGuard();
            DisputeBootstrapAction action = transition.getAction();
            DisputeState target = guard.getTargetState();

            var configurer = transitions
                    .withExternal()
                    .source(DisputeState.BOOTSTRAPING_DISPUTE_CONTEXT)
                    .target(target)
                    .event(guard.trigger())
                    .guard(guard);

            // Conditionally add the action
            //var resolvedAction = actionRegistry.getAction(stateMachineMode(), guard.trigger());
            if (action != null) {
                configurer = configurer.action(action);
            }

            configurer.and();
        }

        wiringEngine.wireTransitions(transitions, otherTransitionRegistry.getTransitions());

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
}

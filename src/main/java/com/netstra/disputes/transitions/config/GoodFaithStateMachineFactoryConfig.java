package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.BootstrapTransition;
import com.netstra.disputes.transitions.bootstrap.BootstrapLifecycleRegistry;
import com.netstra.disputes.transitions.listener.GlobalStateMachineListener;
import com.netstra.disputes.transitions.util.OtherTransitionRegistry;
import com.netstra.disputes.transitions.util.StateMachineWiringEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

import java.util.*;

@Configuration
@EnableStateMachineFactory(name="goodfaithStateMachineFactory")
@RequiredArgsConstructor
public class GoodFaithStateMachineFactoryConfig
        extends EnumStateMachineConfigurerAdapter<DisputeState, DisputeTransitionEvent>   implements StateMachineContract{

    private final StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> persister;

    private final GlobalStateMachineListener globalListener;

    private final BootstrapLifecycleRegistry bootstrapRegistry;

    private final StateMachineWiringEngine wiringEngine;

    private final OtherTransitionRegistry otherTransitionRegistry;



    @Override
    public DisputeMode stateMachineMode() {
        return DisputeMode.GOODFAITH;
    }


    @Override
    public void configure(StateMachineStateConfigurer<DisputeState, DisputeTransitionEvent> states) throws Exception {
        states.withStates()
                .initial(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT)
                .states(EnumSet.allOf(DisputeState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<DisputeState, DisputeTransitionEvent> transitions) throws Exception {

        BootstrapTransition gfBootstrapTransition = bootstrapRegistry.getTransitions(stateMachineMode());

        if(null != gfBootstrapTransition){
            for(DisputeState targetState : gfBootstrapTransition.getTargetStates()){
                var configurer = transitions
                        .withExternal()
                        .source(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT)
                        .target(targetState)
                        .event(gfBootstrapTransition.getTriggerEvent());

                Guard<DisputeState, DisputeTransitionEvent> guard = gfBootstrapTransition.getGuard();
                Action<DisputeState, DisputeTransitionEvent> action = gfBootstrapTransition.getAction();

                if (guard != null) {
                    configurer = configurer.guard(guard);
                }

                if (action != null) {
                    configurer = configurer.action(action);
                }

                configurer.and();
            }
        }


//        wiringEngine.wireTransitions(transitions,
//                otherTransitionRegistry.getOtherTransitionByDisputeMode(
//                        stateMachineMode()).getTransitions()
//        );
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

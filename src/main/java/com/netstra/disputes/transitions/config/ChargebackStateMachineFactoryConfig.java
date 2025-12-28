package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.actions.CBEvidenceProcessedAction;
import com.netstra.disputes.transitions.actions.CBWithdrawnAction;
import com.netstra.disputes.transitions.bootstrap.BootstrapLifecycleRegistry;
import com.netstra.disputes.transitions.bootstrap.BootstrapTransition;
import com.netstra.disputes.transitions.guards.CBEvidenceProcessedGuard;
import com.netstra.disputes.transitions.guards.CBWithdrawnGuard;
import com.netstra.disputes.transitions.listener.GlobalStateMachineListener;
import com.netstra.disputes.transitions.util.OtherTransitionRegistry;
import com.netstra.disputes.transitions.util.StateMachineWiringEngine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
@EnableStateMachineFactory(name="chargebackStateMachineFactory")
@RequiredArgsConstructor
public class ChargebackStateMachineFactoryConfig
        extends EnumStateMachineConfigurerAdapter<DisputeState, DisputeTransitionEvent>  implements StateMachineContract{

    private final StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> persister;

    private final GlobalStateMachineListener globalListener;

    private final BootstrapLifecycleRegistry bootstrapRegistry;

    private final OtherTransitionRegistry otherTransitionRegistry;
    private final StateMachineWiringEngine wiringEngine;
    private final CBWithdrawnGuard withdrawnGuard;
    private final CBWithdrawnAction withdrawnAction;
    @Override
    public DisputeMode stateMachineMode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public void configure(StateMachineStateConfigurer<DisputeState, DisputeTransitionEvent> states) throws Exception {

        // Build hierarchy in a fluent style
        states.withStates()
                .initial(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT)
                .state(DisputeState.ACTIVE)
                .state(DisputeState.TERMINAL)
                .state(DisputeState.WITHDRAWN)

                // Active children
                .and().withStates()
                .parent(DisputeState.ACTIVE)
                .initial(DisputeState.ACTIVE_ENTRY)
                .states(DisputeState.getAllActiveChildren())

                // Terminal children
                .and().withStates()
                .parent(DisputeState.TERMINAL)
                .initial(DisputeState.TERMINAL_ENTRY)
                .states(DisputeState.getAllTerminalChildren())

                // Withdrawn children
                .and().withStates()
                .parent(DisputeState.WITHDRAWN)
                .initial(DisputeState.WITHDRAWN_ENTRY)
                .states(DisputeState.getAllWithdrawnChildren());
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<DisputeState, DisputeTransitionEvent> transitions) throws Exception {
       //handles bootstrap transitions to all the possible active states
        BootstrapTransition chbkBootstrapTransition = bootstrapRegistry.getTransitions(stateMachineMode());

        if(null != chbkBootstrapTransition){
            for(DisputeState targetState : chbkBootstrapTransition.getTargetStates()){
                var configurer = transitions
                        .withExternal()
                        .source(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT)
                        .target(targetState)
                        .event(chbkBootstrapTransition.getTriggerEvent());

                Guard<DisputeState, DisputeTransitionEvent> guard = chbkBootstrapTransition.getGuard();
                Action<DisputeState, DisputeTransitionEvent> action = chbkBootstrapTransition.getAction();

                if (guard != null) {
                    configurer = configurer.guard(guard);
                }

                if (action != null) {
                    configurer = configurer.action(action);
                }

                configurer.and();
            }
        }

        //handles active state to all withdrawn state

        transitions
                .withExternal()
                .source(DisputeState.ACTIVE)  // will i need a  wildcard (such as ACTIVE.*) Parent = ALL active children
                .target(DisputeState.WITHDRAWN)
                .event(DisputeTransitionEvent.EVENT_CUSTOMER_WITHDRAWS)
                .guard(withdrawnGuard)
                .action(withdrawnAction)

                .and().withExternal()
                .source(DisputeState.ACTIVE)
                .target(DisputeState.WITHDRAWN)
                .event(DisputeTransitionEvent.EVENT_PLAINTIFF_WITHDRAWS)
                .guard(withdrawnGuard)
                .action(withdrawnAction)

                .and().withExternal()
                .source(DisputeState.ACTIVE)
                .target(DisputeState.WITHDRAWN)
                .event(DisputeTransitionEvent.EVENT_RESPONDER_WITHDRAWS)
                .guard(withdrawnGuard)
                .action(withdrawnAction)

                .and().withExternal()
                .source(DisputeState.ACTIVE)
                .target(DisputeState.WITHDRAWN)
                .event(DisputeTransitionEvent.EVENT_SUB_INSTITUTION_WITHDRAWS)
                .guard(withdrawnGuard)
                .action(withdrawnAction);


        //handles only intra parent state transitions
        wiringEngine.wireTransitions(transitions,
                otherTransitionRegistry.getOtherTransitionByDisputeMode(
                        stateMachineMode()).getTransitions()
        );

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

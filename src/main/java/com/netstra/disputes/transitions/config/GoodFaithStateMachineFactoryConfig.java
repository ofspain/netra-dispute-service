package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.BootstrapTransition;
import com.netstra.disputes.transitions.bootstrap.action.DisputeBootstrapAction;
import com.netstra.disputes.transitions.bootstrap.BootstrapLifecycleRegistry;
import com.netstra.disputes.transitions.guards.GFGuardAwaitAcqVerification;
import com.netstra.disputes.transitions.guards.GFGuardAwaitIssVerification;
import com.netstra.disputes.transitions.bootstrap.guard.DisputeBootstrapGuard;
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
@EnableStateMachineFactory(name="goodfaithStateMachineFactory")
@RequiredArgsConstructor
public class GoodFaithStateMachineFactoryConfig
        extends EnumStateMachineConfigurerAdapter<DisputeState, DisputeTransitionEvent>   implements StateMachineContract{

    private final StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> persister;

    private final GlobalStateMachineListener globalListener;

    private final BootstrapLifecycleRegistry bootstrapRegistry;

    // Inject individual phase-level guards
    private final GFGuardAwaitAcqVerification guardAwaitAcqVerification;
    private final GFGuardAwaitIssVerification guardAwaitIssVerification;

    @Override
    public DisputeMode stateMachineMode() {
        return DisputeMode.GOODFAITH;
    }


    @Override
    public void configure(StateMachineStateConfigurer<DisputeState, DisputeTransitionEvent> states) throws Exception {
        states.withStates()
                .initial(DisputeState.BOOTSTRAPING_DISPUTE_CONTEXT)
                .states(EnumSet.allOf(DisputeState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<DisputeState, DisputeTransitionEvent> transitions) throws Exception {

        Collection<BootstrapTransition> gfBootstrapTransition = bootstrapRegistry.getTransitions(stateMachineMode());
        for (BootstrapTransition transition : gfBootstrapTransition) {
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


        /*
         * -------------------------------
         * 2️⃣ Acquirer phase transitions
         * -------------------------------
         * todo: try change to config from yaml
         */
        transitions
                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.ACQUIRER_VERIFIED)
                .event(DisputeTransitionEvent.ACQUIRER_VERIFIES)
                .guard(guardAwaitAcqVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.ACQUIRER_DECLINED)
                .event(DisputeTransitionEvent.ACQUIRER_DECLINES)
                .guard(guardAwaitAcqVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.EXPIRED)
                .event(DisputeTransitionEvent.EXPIRES)
                .guard(guardAwaitAcqVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_CUSTOMER)
                .event(DisputeTransitionEvent.CUSTOMER_WITHDRAWS)
                .guard(guardAwaitAcqVerification)
                .action(null)//add appropraite action here
                .and()


                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_ISSUER)
                .event(DisputeTransitionEvent.ISSUER_WITHDRAWS)
                .guard(guardAwaitAcqVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ACQUIRER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_SUB_INSTITUTION)
                .event(DisputeTransitionEvent.SUB_INST_WITHDRAWS)
                .guard(guardAwaitAcqVerification)
                .action(null) //add appropraite action here
                .and();




        /*
         * -------------------------------
         * 3️⃣ Issuer phase transitions
         * -------------------------------
         */
        transitions
                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.ISSUER_VERIFIED)
                .event(DisputeTransitionEvent.ISSUER_VERIFIES)
                .guard(guardAwaitIssVerification)
                .action(null)//add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.ISSUER_DECLINED)
                .event(DisputeTransitionEvent.ISSUER_DECLINES)
                .guard(guardAwaitIssVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.EXPIRED)
                .event(DisputeTransitionEvent.EXPIRES)
                .guard(guardAwaitIssVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_CUSTOMER)
                .event(DisputeTransitionEvent.CUSTOMER_WITHDRAWS)
                .guard(guardAwaitIssVerification)

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_ACQUIRER)
                .event(DisputeTransitionEvent.ACQUIRER_WITHDRAWS)
                .guard(guardAwaitIssVerification)
                .action(null) //add appropraite action here

                .and()

                .withExternal()
                .source(DisputeState.AWAITING_ISSUER_VERIFICATION)
                .target(DisputeState.WITHDRAWN_SUB_INSTITUTION)
                .event(DisputeTransitionEvent.SUB_INST_WITHDRAWS)
                .guard(guardAwaitIssVerification)
                .action(null) //add appropraite action here
                .and();
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

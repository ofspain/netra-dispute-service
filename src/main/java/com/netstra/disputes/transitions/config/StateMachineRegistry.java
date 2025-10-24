package com.netstra.disputes.transitions.config;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StateMachineRegistry {

    private final Map<DisputeMode, StateMachineFactory<DisputeState, DisputeTransitionEvent>> factories;

    public StateMachineRegistry(
            @Qualifier("chargebackStateMachineFactory")
            StateMachineFactory<DisputeState, DisputeTransitionEvent> chargebackFactory,

            @Qualifier("refundStateMachineFactory")
            StateMachineFactory<DisputeState, DisputeTransitionEvent> refundFactory,

            @Qualifier("goodfaithStateMachineFactory")
            StateMachineFactory<DisputeState, DisputeTransitionEvent> goodfaithFactory

            // Add other factories as needed
    ) {

        this.factories = Map.of(
                DisputeMode.CHARGEBACK, chargebackFactory,
                DisputeMode.REFUND, refundFactory,
                DisputeMode.GOODFAITH, goodfaithFactory
                // Add other modes
        );
    }

    public StateMachineFactory<DisputeState, DisputeTransitionEvent> getStateMachineFactory(DisputeMode mode) {
        StateMachineFactory<DisputeState, DisputeTransitionEvent> factory = factories.get(mode);
        if (factory == null) {
            throw new IllegalArgumentException("No state machine factory found for mode: " + mode);
        }
        return factory;
    }
}


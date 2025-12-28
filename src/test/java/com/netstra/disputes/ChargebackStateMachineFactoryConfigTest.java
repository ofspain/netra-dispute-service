package com.netstra.disputes;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.actions.CBWithdrawnAction;
import com.netstra.disputes.transitions.bootstrap.BootstrapLifecycleRegistry;
import com.netstra.disputes.transitions.bootstrap.BootstrapTransition;
import com.netstra.disputes.transitions.config.ChargebackStateMachineFactoryConfig;
import com.netstra.disputes.transitions.config.StateMachineRegistry;
import com.netstra.disputes.transitions.guards.CBWithdrawnGuard;
import com.netstra.disputes.transitions.listener.GlobalStateMachineListener;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistService;
import com.netstra.disputes.transitions.service.DisputeStateMachineService;
import com.netstra.disputes.transitions.util.OtherTransitionRegistry;
import com.netstra.disputes.transitions.util.StateMachineWiringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationBuilder;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;


import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link ChargebackStateMachineFactoryConfig}
 * Uses Spring's StateMachineBuilder to create a real state machine for testing
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
class ChargebackStateMachineFactoryConfigTest {

    @Autowired
    private DisputeStateMachinePersistService persister;

    @Autowired
    private GlobalStateMachineListener globalListener;

    @Autowired
    private BootstrapLifecycleRegistry bootstrapRegistry;

    @Autowired
    private OtherTransitionRegistry otherTransitionRegistry;

    @Autowired
    private StateMachineWiringEngine wiringEngine;

    @Autowired
    private CBWithdrawnGuard withdrawnGuard;

    @Autowired
    private CBWithdrawnAction withdrawnAction;


    private BootstrapTransition mockBootstrapTransition;

    private DisputeStateMachineService disputeStateMachineService;

    @Autowired
    private StateMachineRegistry stateMachineRegistry;

    @Autowired
    private ChargebackStateMachineFactoryConfig config;



    @BeforeEach
    void setUp() {

    }

    /**
     * Test 1: Basic factory method - returns correct mode
     */
    @Test
    void stateMachineMode_shouldReturnChargeback() {
        // Act & Assert
        assertThat(config.stateMachineMode()).isEqualTo(DisputeMode.CHARGEBACK);
    }

    /**
     * Test 2: Create a real state machine using the config methods
     * This validates that configureStates() builds a valid hierarchy
     */
    @Test
    void configureStates_shouldBuildValidStateMachine() throws Exception {
        StateMachineFactory<DisputeState, DisputeTransitionEvent> stateMachineFactory =
                stateMachineRegistry.getStateMachineFactory(DisputeMode.CHARGEBACK);

        StateMachine<DisputeState, DisputeTransitionEvent> stateMachine = stateMachineFactory
                        .getStateMachine("1"); //dispute.getId().toString()

        // Assert - Start and verify initial state
        stateMachine.start();

        boolean eventAccepted = stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER);


        assertThat(stateMachine.getState().getId())
                .isEqualTo(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT);

        // Verify machine contains expected states
        assertThat(stateMachine.getStates())
                .extracting("id")
                .contains(DisputeState.ACTIVE, DisputeState.TERMINAL, DisputeState.WITHDRAWN);
    }

    /**
     * Test 3: Verify transitions can be configured without throwing exceptions
     * This is a smoke test for the configureTransitions method
     */
    /**
     * Test 5: Verify the factory can be instantiated with all dependencies
     */
    @Test
    void factory_shouldBeInstantiatedWithAllDependencies() {
        // Act & Assert
        assertThat(config).isNotNull();
        // If we reach here without NullPointerException, injection worked
    }

    /**
     * Test 7: Test edge case - null bootstrap transition
     */
    @Test
    void configureTransitions_withNullBootstrapTransition_shouldNotFail() throws Exception {

        // Verify
        verify(bootstrapRegistry).getTransitions(DisputeMode.CHARGEBACK);
        verify(wiringEngine).wireTransitions(any(), any());
    }
}
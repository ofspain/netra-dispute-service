package com.netstra.disputes;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistService;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistenceInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptor;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;

@TestConfiguration
@Slf4j
class StateMachinePersisterTestConfig {

    @Bean
    @Primary
    public StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> trackingRuntimePersister(
            DisputeStateMachinePersistService persistService,
            DisputeStateMachinePersistenceInterceptor interceptor) {

        return new StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String>() {

            @Override
            public StateMachineInterceptor<DisputeState, DisputeTransitionEvent> getInterceptor() {
                StateMachinePersisterLifecycleTest.logMethodCall("getInterceptor()");
                log.info("✓ getInterceptor() called - returning wrapped interceptor");

                // Wrap the existing interceptor to track its method calls
                return new StateMachineInterceptorAdapter<DisputeState, DisputeTransitionEvent>() {

                    @Override
                    public Message<DisputeTransitionEvent> preEvent(
                            Message<DisputeTransitionEvent> message,
                            StateMachine<DisputeState, DisputeTransitionEvent> stateMachine) {
                        StateMachinePersisterLifecycleTest.logMethodCall("preEvent");
                        return interceptor.preEvent(message, stateMachine);
                    }

                    @Override
                    public void preStateChange(
                            State<DisputeState, DisputeTransitionEvent> state,
                            Message<DisputeTransitionEvent> message,
                             Transition<DisputeState, DisputeTransitionEvent> transition,
                            StateMachine<DisputeState, DisputeTransitionEvent> stateMachine,
                            StateMachine<DisputeState, DisputeTransitionEvent> rootStateMachine) {
                        StateMachinePersisterLifecycleTest.logMethodCall("preStateChange");
                        interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);
                    }

                    @Override
                    public void postStateChange(
                            State<DisputeState, DisputeTransitionEvent> state,
                            Message<DisputeTransitionEvent> message,
                            Transition<DisputeState, DisputeTransitionEvent> transition,
                            StateMachine<DisputeState, DisputeTransitionEvent> stateMachine,
                            StateMachine<DisputeState, DisputeTransitionEvent> rootStateMachine) {
                        StateMachinePersisterLifecycleTest.logMethodCall("postStateChange");
                        interceptor.postStateChange(state, message, transition, stateMachine, rootStateMachine);
                    }

                    @Override
                    public StateContext<DisputeState, DisputeTransitionEvent> preTransition(
                            StateContext<DisputeState, DisputeTransitionEvent> stateContext) {
                        StateMachinePersisterLifecycleTest.logMethodCall("preTransition");
                        return interceptor.preTransition(stateContext);
                    }

                    @Override
                    public StateContext<DisputeState, DisputeTransitionEvent> postTransition(
                            StateContext<DisputeState, DisputeTransitionEvent> stateContext) {
                        StateMachinePersisterLifecycleTest.logMethodCall("postTransition");
                        return interceptor.postTransition(stateContext);
                    }
                };
            }

            @Override
            public StateMachineContext<DisputeState, DisputeTransitionEvent> read(String contextObj) throws Exception {
                StateMachinePersisterLifecycleTest.logMethodCall("read(" + contextObj + ")");
                log.info("✓ read() called for context: {}", contextObj);
                return persistService.read(contextObj);
            }

            @Override
            public void write(StateMachineContext<DisputeState, DisputeTransitionEvent> context, String contextObj) throws Exception {
                StateMachinePersisterLifecycleTest.logMethodCall("write(" + contextObj + ", state=" + context.getState() + ")");
                log.info("✓ write() called for context: {}, state: {}", contextObj, context.getState());
                persistService.write(context, contextObj);
            }
        };
    }
}

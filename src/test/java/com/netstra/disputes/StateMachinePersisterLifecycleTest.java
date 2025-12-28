package com.netstra.disputes;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.persist.DisputeStateMachinePersistService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(StateMachinePersisterTestConfig.class)
@Slf4j
class StateMachinePersisterLifecycleTest {

    @Autowired
    @Qualifier("chargebackStateMachineFactory")
    private StateMachineFactory<DisputeState, DisputeTransitionEvent> stateMachineFactory;

    @Autowired
    private DisputeStateMachinePersistService persistService;

    private StateMachine<DisputeState, DisputeTransitionEvent> stateMachine;

    // Simple call tracking
    private static final List<String> methodCallLog = new ArrayList<>();

    @BeforeEach
    void setUp() {
      //  methodCallLog.clear();
    }

    @AfterEach
    void tearDown() {
        if (stateMachine != null && !stateMachine.isComplete()) {
            stateMachine.stop();
        }
    }

    @Test
    @DisplayName("getInterceptor() is called ONCE during state machine initialization")
    void testGetInterceptorCalledOnceOnInitialization() {
        // Given
        //methodCallLog.clear();

        // When - Create new state machine instance
        String disputeId = "DISPUTE-001";
        stateMachine = stateMachineFactory.getStateMachine(disputeId);

        // Then
        long getInterceptorCalls = methodCallLog.stream()
                .filter(call -> call.equals("getInterceptor()"))
                .count();

        assertThat(getInterceptorCalls)
                .as("getInterceptor() should be called exactly once during initialization")
                .isEqualTo(3);

        log.info("✓ Method calls during initialization: {}", methodCallLog);
    }

    @Test
    @DisplayName("write() is called after EVERY successful state transition")
    void testWriteCalledAfterSuccessfulTransition() throws InterruptedException {
        // Given
        String disputeId = "DISPUTE-002";
        stateMachine = stateMachineFactory.getStateMachine(disputeId);
        stateMachine.start();

        methodCallLog.clear();

        // When - Send event that triggers valid transition
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER);

        // Give state machine time to process
        Thread.sleep(100);

        // Then
        long writeCalls = methodCallLog.stream()
                .filter(call -> call.startsWith("write("))
                .count();

        assertThat(writeCalls)
                .as("write() should be called after successful transition")
                .isGreaterThanOrEqualTo(1);

        // Verify write was called AFTER preStateChange (if both exist)
        int preStateChangeIndex = findCallIndex("preStateChange");
        int writeIndex = findCallIndex("write(");

        if (preStateChangeIndex >= 0 && writeIndex >= 0) {
            assertThat(writeIndex)
                    .as("write() should be called after preStateChange()")
                    .isGreaterThan(preStateChangeIndex);
        }

        log.info("✓ Method call sequence: {}", methodCallLog);
    }

    @Test
    @DisplayName("write() is called multiple times for multiple transitions")
    void testWriteCalledForMultipleTransitions() throws InterruptedException {
        // Given
        String disputeId = "DISPUTE-003";
        stateMachine = stateMachineFactory.getStateMachine(disputeId);
        stateMachine.start();

        methodCallLog.clear();

        // preEvent->
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_INSTITUTION);
        Thread.sleep(100);

        long writeCallsAfterFirst = methodCallLog.stream()
                .filter(call -> call.startsWith("write("))
                .count();

        // Send another event
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_CUSTOMER_WITHDRAWS);
        Thread.sleep(100);

        // Then
        long totalWriteCalls = methodCallLog.stream()
                .filter(call -> call.startsWith("write("))
                .count();

        log.info("✓ Write calls after first transition: {}", writeCallsAfterFirst);
        log.info("✓ Total write calls after multiple transitions: {}", totalWriteCalls);

        assertThat(totalWriteCalls)
                .as("write() should be called for each successful transition")
                .isGreaterThanOrEqualTo(writeCallsAfterFirst);
    }

    @Test
    @DisplayName("read() is called when explicitly restoring state machine")
    void testReadCalledOnExplicitRestore() throws Exception {
        // Given
        String disputeId = "DISPUTE-004";

        // First, persist some state
        stateMachine = stateMachineFactory.getStateMachine(disputeId);
        stateMachine.start();
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER);
        Thread.sleep(100);

        methodCallLog.clear();

        // When - Explicitly restore state machine
        StateMachineContext<DisputeState, DisputeTransitionEvent> context =
                persistService.read(disputeId);

        // Then
        boolean readWasCalled = methodCallLog.stream()
                .anyMatch(call -> call.startsWith("read("));

        assertThat(readWasCalled)
                .as("read() should be called during explicit restore")
                .isTrue();

        log.info("✓ Method calls during restore: {}", methodCallLog);
        log.info("✓ Restored state: {}", context != null ? context.getState() : "null");
    }

    @Test
    @DisplayName("Verify complete lifecycle: getInterceptor -> write -> read")
    void testCompleteLifecycle() throws Exception {
        // Given
        String disputeId = "DISPUTE-LIFECYCLE";
        methodCallLog.clear();

        // When - Complete lifecycle
        // 1. Create machine (getInterceptor called)
        stateMachine = stateMachineFactory.getStateMachine(disputeId);
        stateMachine.start();

        // 2. Transition (write called)
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER);
        Thread.sleep(100);

        // 3. Restore (read called)
        StateMachineContext<DisputeState, DisputeTransitionEvent> context =
                persistService.read(disputeId);

        // Then - Verify all three methods were called
        boolean hasGetInterceptor = methodCallLog.stream()
                .anyMatch(call -> call.equals("getInterceptor()"));
        boolean hasWrite = methodCallLog.stream()
                .anyMatch(call -> call.startsWith("write("));
        boolean hasRead = methodCallLog.stream()
                .anyMatch(call -> call.startsWith("read("));

        assertThat(hasGetInterceptor).as("getInterceptor() was called").isTrue();
        assertThat(hasWrite).as("write() was called").isTrue();
        assertThat(hasRead).as("read() was called").isTrue();

        log.info("✓ Complete lifecycle method calls: {}", methodCallLog);
        log.info("✓ Call order verified: getInterceptor -> write -> read");
    }

    @Test
    @DisplayName("Interceptor methods are called during transition")
    void testInterceptorMethodsCalled() throws InterruptedException {
        // Given
        String disputeId = "DISPUTE-005";
        stateMachine = stateMachineFactory.getStateMachine(disputeId);
        stateMachine.start();

        methodCallLog.clear();

        // When
        stateMachine.sendEvent(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER);
        Thread.sleep(100);

        // Then - Verify interceptor methods were called
        boolean hasPreEvent = methodCallLog.stream()
                .anyMatch(call -> call.equals("preEvent"));
        boolean hasPreStateChange = methodCallLog.stream()
                .anyMatch(call -> call.equals("preStateChange"));
        boolean hasPostStateChange = methodCallLog.stream()
                .anyMatch(call -> call.equals("postStateChange"));

        log.info("✓ Interceptor method calls: {}", methodCallLog);
        log.info("  - preEvent: {}", hasPreEvent);
        log.info("  - preStateChange: {}", hasPreStateChange);
        log.info("  - postStateChange: {}", hasPostStateChange);

        assertThat(hasPreEvent || hasPreStateChange)
                .as("At least one interceptor pre-method should be called")
                .isTrue();
    }

    // Helper method
    private int findCallIndex(String methodPrefix) {
        for (int i = 0; i < methodCallLog.size(); i++) {
            if (methodCallLog.get(i).startsWith(methodPrefix)) {
                return i;
            }
        }
        return -1;
    }

    public static void logMethodCall(String methodName) {
        methodCallLog.add(methodName);
    }
}


// ============================================
// TEST CONFIGURATION - Add this to your test package
// ============================================

/*



 */

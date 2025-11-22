package com.netstra.disputes.transitions.persist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netstra.disputes.exceptions.StateMachineNotFoundException;
import com.netstra.disputes.model.ContextWrapper;
import com.netstra.disputes.model.DisputeStateMachineEntity;
import com.netstra.disputes.security.DomainAwarePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DisputeStateMachinePersistService
        implements StateMachinePersist<DisputeState, DisputeTransitionEvent, String> {

    private DisputeStateMachinePersistenceDao persistenceDao;

    private final ObjectMapper objectMapper;

    @Override/* ensure dispute id is in the form disputeMode:disputeId**/
    public void write(StateMachineContext<DisputeState, DisputeTransitionEvent> context, String disputeId) {
        try {
            DisputeStateMachineEntity entity = persistenceDao.findByMachineId(disputeId)
                    .orElse(new DisputeStateMachineEntity());

            entity.setMachineId(disputeId);
            entity.setCurrentState(context.getState());
            entity.setContext(serializeContext(context)); // Convert to JSON
            entity.setVersion(entity.getVersion() + 1);
            entity.setLastEvent(context.getEvent());

            persistenceDao.save(entity);
            log.debug("Persisted state machine for dispute: {}, state: {}", disputeId, context.getState());

        } catch (Exception e) {
            log.error("Failed to persist state machine for dispute: {}", disputeId, e);
            throw new RuntimeException("Persistence failed for dispute: " + disputeId, e);
        }
    }

    @Override
    public StateMachineContext<DisputeState, DisputeTransitionEvent> read(String disputeId) {
        try {
            Optional<DisputeStateMachineEntity> entityOpt = persistenceDao.findByMachineId(disputeId);

            if (entityOpt.isEmpty()) {
                // 🛡️ SAFE: Return null to indicate fresh state machine
                // The StateMachine factory will use configured initial state
                log.debug("No persisted context found for new dispute: {}, using initial state", disputeId);
                return null; // This is OK - framework handles it
            }
            DisputeStateMachineEntity entity = entityOpt.get();

            return deserializeContext(entity.getContext());

        } catch (Exception e) {
            log.error("Error reading state machine context for dispute: {}", disputeId, e);
            // 🛡️ SAFE: Return null instead of throwing - let framework use initial state
            return null;
        }
    }

    // 🎯 CRITICAL: Serialize context to JSON
    private String serializeContext(StateMachineContext<DisputeState, DisputeTransitionEvent> context) {
        try {
            ContextWrapper wrapper = new ContextWrapper(
                    context.getState(),
                    context.getEvent(),
                    convertVariablesToSerializable(context.getVariables()),
                    context.getEventHeaders(),
                    context.getHistoryStates(),
                    context.getChilds()
            );
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize state machine context", e);
        }
    }

    // 🎯 CRITICAL: Deserialize JSON to context
    private StateMachineContext<DisputeState, DisputeTransitionEvent> deserializeContext(String json) {
        try {
            ContextWrapper wrapper = objectMapper.readValue(json, ContextWrapper.class);
            return new DefaultStateMachineContext<>(
                    wrapper.getState(),
                    wrapper.getEvent(),
                    wrapper.getEventHeaders(),
                    convertToExtendedState(wrapper.getVariables()),
                    wrapper.getHistoryStates(),
                    wrapper.getChilds()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize state machine context", e);
        }
    }

    // 🚀 Only store serializable data (avoid large objects)
    private Map<String, Object> convertVariablesToSerializable(Map<Object, Object> variables) {
        Map<String, Object> serializable = new HashMap<>();

        for (Map.Entry<Object, Object> entry : variables.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            // Only store simple, serializable data
            if (isSerializable(value)) {
                serializable.put(key, value);
            } else if (value instanceof Dispute dispute) {
                serializable.put(key, Map.of("id", dispute.getId())); // Store only ID
            } else if (value instanceof DomainAwarePrincipal user) {
                serializable.put(key, Map.of("id", user.getIDonHostDB())); // Store only ID
            }
            // Skip large/unserializable objects
        }
        return serializable;
    }

    private boolean isSerializable(Object obj) {
        return obj instanceof String || obj instanceof Number ||
                obj instanceof Boolean || obj instanceof Collection ||
                obj instanceof Map || obj == null;
    }
}


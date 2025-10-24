package com.netstra.disputes.transitions.persist;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DisputeStateMachinePersistService
        implements StateMachinePersist<DisputeState, DisputeTransitionEvent, String> {

    @Autowired
    private DisputeStateMachinePersistenceDao persistenceDao;

    @Override
    public void write(StateMachineContext<DisputeState, DisputeTransitionEvent> context, String entityId)
            throws Exception {
        byte[] bytes = null;//SerializationUtils.serialize(context);
        DisputeStateMachineEntity entity = new DisputeStateMachineEntity();
        entity.setId(entityId);
        entity.setStateMachineContext(bytes);
        entity.setLastUpdated(LocalDateTime.now());
        persistenceDao.save(entity);
    }

    @Override
    public StateMachineContext<DisputeState, DisputeTransitionEvent> read(String entityId)
            throws Exception {
        DisputeStateMachineEntity entity = persistenceDao.findByEntityId(entityId);

        if (entity == null) return null;

        return (StateMachineContext<DisputeState, DisputeTransitionEvent>)
                SerializationUtils.deserialize(entity.getStateMachineContext());
    }

    // Helper method to persist entire state machine
    public void persist(StateMachine<DisputeState, DisputeTransitionEvent> stateMachine, String entityId)
            throws Exception {
        StateMachineContext<DisputeState, DisputeTransitionEvent> context =
                new DefaultStateMachineContext<>(
                        stateMachine.getState().getId(),
                        null,
                        null,
                        stateMachine.getExtendedState(),
                        null,
                        stateMachine.getId()
                );
        write(context, entityId);
    }

}


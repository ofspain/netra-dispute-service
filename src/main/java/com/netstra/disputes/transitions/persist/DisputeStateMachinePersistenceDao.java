package com.netstra.disputes.transitions.persist;

import com.netstra.disputes.model.DisputeStateMachineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DisputeStateMachinePersistenceDao  extends JpaRepository<DisputeStateMachineEntity, Long> {


    Optional<DisputeStateMachineEntity> findByMachineId(String machineID);
}

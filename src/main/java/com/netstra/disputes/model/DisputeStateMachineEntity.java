package com.netstra.disputes.model;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.util.BasicUtil;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_state_machine_entities")
@Data
public class DisputeStateMachineEntity {

    public static final String ID_MODE_SEPARATOR = ":";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Format: <mode>:<id>
     * Example: CHARGEBACK:123
     */
    @Column(nullable = false, unique = true)
    private String machineId;

    @Enumerated(EnumType.STRING)
    private DisputeState currentState;

    @Enumerated(EnumType.STRING)
    private DisputeTransitionEvent lastEvent;

    private LocalDateTime createdAt;
    private LocalDateTime lastUpdateAt;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String context;

    private Integer version = 0;

    @Transient
    private Long disputeId;

    @Transient
    private DisputeMode disputeMode;

    // -------------------------------------------------------------------------
    // Lifecycle Hooks
    // -------------------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdateAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdateAt = LocalDateTime.now();
    }

    @PostLoad
    public void init() {
        if (machineId == null || !machineId.contains(ID_MODE_SEPARATOR)) {
            disputeId = null;
            disputeMode = null;
            return;
        }

        String[] parts = machineId.split(ID_MODE_SEPARATOR, 2);

        if (parts.length < 2) {
            disputeId = null;
            disputeMode = null;
            return;
        }

        // Safe enum parsing
        disputeMode = BasicUtil.safeEnum(DisputeMode.class, parts[0]);

        // Safe ID parsing
        try {
            disputeId = Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            disputeId = null;
        }
    }

    // -------------------------------------------------------------------------
    // Optional convenience methods
    // -------------------------------------------------------------------------

    public void setDisputeReference(DisputeMode mode, Long id) {
        this.machineId = mode + ID_MODE_SEPARATOR + id;
    }

    public boolean hasValidReference() {
        return disputeId != null && disputeMode != null;
    }

    // Getters & setters omitted for brevity, but can be generated
}



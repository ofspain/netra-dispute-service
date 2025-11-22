package com.netstra.disputes.model;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// Helper class for JSON serialization
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContextWrapper {
    private DisputeState state;
    private DisputeTransitionEvent event;
    private Map<String, Object> variables;
    private Map<String, Object> eventHeaders;
    private Map<String, Object> historyStates;
    private Map<String, Object> childs;
}

package com.netstra.disputes.requests;

import com.netra.commons.contracts.Disputant;
import com.netra.commons.enums.ApplicationChannel;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.requests.DatedSearchParams;
import com.netra.commons.requests.SortedSearchParams;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class DisputeSearchParams extends DatedSearchParams implements SortedSearchParams {

    private LocalDateTime disputeMarkedLegitTimeStartDate;
    private LocalDateTime disputeMarkedLegitTimeEndDate;

    private DisputeState currentState;
    private DisputeState previousState;

    private ApplicationChannel applicationChannel;
    private Disputant disputant;//identity and identity uuid must be set
    private String issuerCode;

    private String acquirerCode;
    private String merchantCode;
    private String beneficiaryCode;
    private String switcherCode;

    private ApplicationChannel createdBy;

    private boolean locked;
    private boolean isFinalized;
    private boolean isResolved;
    private boolean resolvedInCustomerFavor;

    private String sortColumn;
    private SortDirection sortDirection;


    public void setSortDirection(SortDirection sortDirection){
        this.sortDirection = sortDirection;
    }

    public void setSortColumn(String sortColumn){
        if(allowedSortColumns().contains(sortColumn)){
            this.sortColumn = sortColumn;
        }else {
            this.sortColumn = "created_at";
        }
    }

    public SortDirection getSortDirection() {
        return sortDirection;
    }

    public String getSortColumn() {
        return sortColumn;
    }

    public Set<String> allowedSortColumns(){
        return Set.of(
                "id","created_at", "updated_at", "dispute_marked_legit_time"
        );
    }

}

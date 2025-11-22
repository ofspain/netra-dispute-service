package com.netstra.disputes.security;

import com.netra.commons.models.Identity;

public interface DomainAwarePrincipal {

    String getUserName();
    String getDomainCode();

    Long getIDonHostDB();

    Boolean getDisabled();

}

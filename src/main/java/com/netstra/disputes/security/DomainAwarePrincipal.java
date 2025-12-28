package com.netstra.disputes.security;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.Identity;

public interface DomainAwarePrincipal {

    String getUserName();
    String getDomainCode();

    String getIdentityUUID();

    DomainType getDomainType();


    Boolean getDisabled();

}

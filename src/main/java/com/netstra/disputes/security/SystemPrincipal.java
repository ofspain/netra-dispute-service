package com.netstra.disputes.security;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.Identity;

public class SystemPrincipal implements DomainAwarePrincipal{

    private final Identity systemIdentity;

    public SystemPrincipal(){
        systemIdentity = Identity.systemIdentity();
    }
    @Override
    public String getUserName() {
        return this.systemIdentity.getUsername();
    }

    @Override
    public String getDomainCode() {
        return this.systemIdentity.getDomainCode();
    }

    @Override
    public String getIdentityUUID() {

        return systemIdentity.getIdentityUuid();
    }

    @Override
    public DomainType getDomainType(){
        return systemIdentity.getDomainType();
    }

    @Override
    public Boolean getDisabled() {
        return systemIdentity.getDisabled();
    }
}

package com.netstra.disputes.security;

public interface DomainAwarePrincipal {

    String getUserName();
//    String getDomainCode();
    String getEffectiveDomainCode();
}

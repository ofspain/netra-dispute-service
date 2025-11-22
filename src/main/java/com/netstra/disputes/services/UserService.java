package com.netstra.disputes.services;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.BaseUser;
import com.netra.commons.models.CustomerUser;
import com.netra.commons.models.Identity;
import com.netra.commons.models.InstitutionUser;
import com.netra.commons.util.BasicUtil;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    public BaseUser loadUserFromDb(Identity identity, DomainType domainType, String domainCode){
        //todo: implement straight load from db using the guranteed unique uuid
        String uuid = identity.getIdentityUuid();
        switch (domainType){
            case CUSTOMER -> {
                CustomerUser customerUser = new CustomerUser();
                customerUser.setIdentity(identity);
            }
            case FINANCIAL_INSTITUTION -> {
                InstitutionUser institutionUser =  new InstitutionUser();
                institutionUser.setIdentity(identity);
                return institutionUser;
            }

        }
        throw new IllegalArgumentException("Domain type "+domainType+" not recongnized");
    }

}

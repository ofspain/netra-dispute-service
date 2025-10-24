package com.netstra.disputes.security;

import com.interswitch.backbone.arbitertransactionstoremanager.shared.model.passport.User;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@ToString
@Getter
public class ClientPrincipal extends Jwt  implements DomainAwarePrincipal{

    private final User user;
    private final String userDomainCode;

    public ClientPrincipal(String tokenValue, Instant issuedAt, Instant expiresAt,
                           Map<String, Object> headers, Map<String, Object> claims, User user, String userDomainCode) {
        super(tokenValue, issuedAt, expiresAt, headers, claims);
        Assert.notNull(user, "user cannot be null");
        this.user = user;
        this.userDomainCode = userDomainCode;

    }

//    @Override
    public String getDomainCode() {
      return user.getDomainCode();
    }

    @Override
    public String getUserName() {
        return user.getUsername();
    }

    @Override
    public String getEffectiveDomainCode() {
        return userDomainCode;
    }
}
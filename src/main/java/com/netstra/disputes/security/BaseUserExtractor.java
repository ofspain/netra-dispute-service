package com.netstra.disputes.security;

import com.netra.commons.enums.DisputantType;
import com.netra.commons.enums.DomainType;
import com.netra.commons.models.BaseUser;
import com.netra.commons.models.CustomerUser;
import com.netra.commons.models.Identity;
import com.netra.commons.models.InstitutionUser;
import com.netra.commons.util.BasicUtil;
import com.netstra.disputes.services.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class BaseUserExtractor {

    private final UserService userService;

    private static final String EXPECTED_ISSUER = "your-auth-service";

    public BaseUser extractUser(Jwt jwt, String expectedDomain) {

        // ---- 1. Extract JWT claims ----
        String username = jwt.getClaimAsString("user_name");
        if (!BasicUtil.validString(username)) {
            throw new IllegalArgumentException("JWT missing required claim: user_name");
        }

        String issuer = jwt.getClaimAsString("iss");
        if (!EXPECTED_ISSUER.equalsIgnoreCase(issuer)) {
            throw new IllegalArgumentException("Invalid JWT issuer: " + issuer);
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("JWT does not contain any roles");
        }

        String userDomainCode = jwt.getClaimAsString("domain_code");
        if (!BasicUtil.validString(userDomainCode) || !userDomainCode.equalsIgnoreCase(expectedDomain)) {
            throw new IllegalArgumentException("Domain mismatch: header=" + expectedDomain + ", token=" + userDomainCode);
        }

        String userDomainTypeStr = jwt.getClaimAsString("domain_type"); // optional if separate from domain_code
        if (!BasicUtil.validString(userDomainTypeStr)) {
            throw new IllegalArgumentException("Domain type not found in token");
        }

        DomainType domainType = BasicUtil.safeEnum(DomainType.class, userDomainTypeStr);

        String authIdentity = jwt.getClaimAsString("identity_uuid");
        if (!BasicUtil.validString(authIdentity)) {
            throw new IllegalArgumentException("Identity UUID not found");
        }


        // ---- 3. Attach identity info from JWT claims ----
        Identity identity = new Identity();
        identity.setUsername(username);
        identity.setDomainCode(userDomainCode);
        identity.setDomainType(domainType);
        identity.setIdentityUuid(authIdentity);

        // ---- 2. Load user from DB ----
        BaseUser user = userService.loadUserFromDb(identity, domainType, userDomainCode);


        return user;
    }
}


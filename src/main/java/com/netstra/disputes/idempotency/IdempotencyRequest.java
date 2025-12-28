package com.netstra.disputes.idempotency;

import com.netstra.disputes.security.DomainAwarePrincipal;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

// Generic request wrapper
@Data
@Builder
public class IdempotencyRequest {

    public static final String IDEM_USER_ID_KEY = "";
    public static final String IDEM_AUTHORIZATION_KEY = "Authorization";

    private Object source;          // HttpServletRequest, Message, JobContext, etc.
    private Map<String, String> headers;
    private Map<String, String> parameters;


    private IdempotencyRequestSourceType sourceType;      // HTTP, MESSAGE, JOB, EVENT
    private IdempotencyOperation operationName;
    private IdempotencyContext.ActorType actorType;
    private DomainAwarePrincipal actor;
    private Optional<String> idempotencyKey = Optional.empty();//optional

    public enum IdempotencyRequestSourceType {
        HTTP, MESSAGE, JOB, EVENT
    }

    public enum IdempotencyOperation{
        CREATE_DISPUTE
    }

    public String actorIdentity(){
        return "ACTOR_"+actor.getIdentityUUID()+"_"+ actor.getDomainType().name() +"_"+actor.getDomainCode();
    }
}

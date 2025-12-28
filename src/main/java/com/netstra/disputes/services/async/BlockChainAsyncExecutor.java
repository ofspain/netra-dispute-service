package com.netstra.disputes.services.async;

import com.aptoslabs.japtos.utils.LogLevel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netra.commons.models.Dispute;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;


import com.aptoslabs.japtos.client.AptosClient;
import com.aptoslabs.japtos.api.AptosConfig;
public class BlockChainAsyncExecutor {

    // Configure with specific log level
    AptosConfig config = AptosConfig.builder()
            .network(AptosConfig.Network.DEVNET)
            .logLevel(LogLevel.INFO)  // Available: DEBUG, INFO, WARN, ERROR
            .build();

    AptosClient client = new AptosClient(config);

    private static final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();

    //hash minimalistic dispute object to be notorized
    private String hashDisputeAsMetadata(Dispute dispute) {
        try {
            // Canonical JSON → stable ordering
            String json = mapper.writeValueAsString(dispute);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] rawHash = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            // Base64 recommended for cross-language compatibility
            return Base64.getEncoder().encodeToString(rawHash);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to hash metadata", ex);
        }
    }

    private void prepareChainData(Dispute dispute){
        //// 1. Compute metadata hash
        //byte[] metadataHash = MetadataHasher.computeSHA256Bytes(dispute); // 32 bytes
        //
        //// 2. Convert all strings to UTF-8 bytes
        //byte[] disputeIdBytes = disputeId.getBytes(StandardCharsets.UTF_8);
        //byte[] newStateBytes = newState.getBytes(StandardCharsets.UTF_8);
        //byte[] oldStateBytes = oldState.getBytes(StandardCharsets.UTF_8);
        //byte[] actorUuidBytes = actorUuid.getBytes(StandardCharsets.UTF_8);
        //byte[] issuerDomainBytes = issuerDomain.getBytes(StandardCharsets.UTF_8);
        //byte[] acquirerDomainBytes = acquirerDomain.getBytes(StandardCharsets.UTF_8);
        //byte[] beneficiaryDomainBytes = beneficiaryDomain.getBytes(StandardCharsets.UTF_8);
        //
        //// 3. Create object ready for Aptos SDK submission
        //Map<String, Object> chainEvent = new HashMap<>();
        //chainEvent.put("dispute_id", disputeIdBytes);
        //chainEvent.put("new_state", newStateBytes);
        //chainEvent.put("old_state", oldStateBytes);
        //chainEvent.put("metadata_hash", metadataHash);
        //chainEvent.put("actor_identity_uuid", actorUuidBytes);
        //chainEvent.put("timestamp", timestamp);
        //chainEvent.put("issuer_domain", issuerDomainBytes);
        //chainEvent.put("acquirer_domain", acquirerDomainBytes);
        //chainEvent.put("beneficiary_domain", beneficiaryDomainBytes);
        //
        //// 4. Submit via Aptos SDK
        //aptosClient.submitDisputeEvent(chainEvent);
    }


}

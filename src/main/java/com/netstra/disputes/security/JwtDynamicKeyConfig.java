package com.netstra.disputes.security;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Configuration
@EnableScheduling
public class JwtDynamicKeyConfig {

    @Value("${authrex.public-key-url}")
    private String publicKeyUrl;

    @Value("${authrex.base-url}")
    private String authrexBaseUrl;

    @Value("${authrex.public-key-refresh-minutes:30}")
    private int refreshMinutes;

    private RSAPublicKey currentPublicKey;

    /**
     * Initialize public key at startup
     */
    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        this.currentPublicKey = fetchPublicKeyFromAuthrex();
        return NimbusJwtDecoder.withPublicKey(currentPublicKey).build();
    }

    /**
     * Automatically refresh public key every X minutes (token rotation support)
     */
    @Scheduled(fixedDelayString = "${authrex.public-key-refresh-minutes}000", initialDelay = 60000)
    public void refreshPublicKey() {
        try {
            log.info("[JWT] Refreshing public key from Authrex...");
            RSAPublicKey newKey = fetchPublicKeyFromAuthrex();

            if (!newKey.equals(currentPublicKey)) {
                currentPublicKey = newKey;
                log.info("[JWT] Public key refreshed successfully");
            } else {
                log.info("[JWT] Public key unchanged");
            }
        } catch (Exception ex) {
            log.error("[JWT] Failed to refresh public key: {}", ex.getMessage());
        }
    }

    /**
     * Fetch and convert the PEM public key
     */
    private RSAPublicKey fetchPublicKeyFromAuthrex() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        log.info("Fetching RSA public key from {}", publicKeyUrl);
        String url = authrexBaseUrl + publicKeyUrl;

        String pem = restTemplate.getForObject(url, String.class);

        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Empty public key received from Authrex");
        }

        return convertPemToPublicKey(pem);
    }

    /**
     * Convert PEM → RSAPublicKey
     */
    private RSAPublicKey convertPemToPublicKey(String pem) throws Exception {
        String clean = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(clean);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        PublicKey publicKey = factory.generatePublic(spec);

        return (RSAPublicKey) publicKey;
    }
}


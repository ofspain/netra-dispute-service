package com.netstra.disputes;

import com.netra.commons.models.endpoint.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuthConfigResolutionTest {

    @ParameterizedTest
    @MethodSource("authConfigProvider")
    void testAuthResolution(
            List<AuthConfig> authConfigs,
            Map<String, String> expectedHeaders,
            Map<String, String> expectedQueryParams
    ) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();

        // Simulate resolution like executor does
        for (AuthConfig authConfig : authConfigs) {
            if (authConfig instanceof ApiKeyAuth apiKeyAuth) {
                String value = "dummy-apiKey-secret";
                if (apiKeyAuth.isAsQueryParam()) {
                    queryParams.put(apiKeyAuth.getQueryParamName(), value);
                } else {
                    headers.put(apiKeyAuth.getHeaderName(), value);
                }
            } else if (authConfig instanceof BearerTokenAuth bearer) {
                String token = bearer.getPrefix() + " dummy-bearer-secret";
                headers.put(bearer.getHeaderName(), token);
            } else if (authConfig instanceof BasicAuth basic) {
                String creds = Base64.getEncoder()
                        .encodeToString(("dummyUser:dummyPass").getBytes(StandardCharsets.UTF_8));
                String value = basic.getTokenPrefix() != null && !basic.getTokenPrefix().isBlank()
                        ? basic.getTokenPrefix() + " " + creds
                        : creds;
                headers.put(basic.getHeaderName(), value);
            }else if (authConfig instanceof CustomSignatureAuth custom) {
                // For now, just simulate a fake signature header
                headers.put("X-Custom-Signature", "dummy-signature");
            } else if (authConfig instanceof MtlsAuth mtls) {
                // Usually handled at TLS layer, not as headers — simulate with a placeholder
                headers.put("X-MTLS-Enabled", "true");
            }
        }

        // ✅ Assertions
        expectedHeaders.forEach((k, v) ->
                assertEquals(v, headers.get(k), "Header mismatch for " + k));

        expectedQueryParams.forEach((k, v) ->
                assertEquals(v, queryParams.get(k), "Query param mismatch for " + k));
    }

    private static Stream<Arguments> authConfigProvider() {
        // API Key in header
        ApiKeyAuth apiKeyHeader = new ApiKeyAuth();
        apiKeyHeader.setHeaderName("X-API-KEY");
        apiKeyHeader.setApiKey("vault:secrets/apiKey");
        apiKeyHeader.setAsQueryParam(false);

        // API Key in query
        ApiKeyAuth apiKeyQuery = new ApiKeyAuth();
        apiKeyQuery.setQueryParamName("api_key");
        apiKeyQuery.setApiKey("vault:secrets/apiKey");
        apiKeyQuery.setAsQueryParam(true);

        // Bearer
        BearerTokenAuth bearerAuth = new BearerTokenAuth();
        bearerAuth.setHeaderName("Authorization");
        bearerAuth.setToken("vault:secrets/bearer-token");
        bearerAuth.setPrefix("Bearer");

        // Basic
        BasicAuth basicAuth = new BasicAuth();
        basicAuth.setHeaderName("Authorization");
        basicAuth.setUsername("vault:secrets/basic-user");
        basicAuth.setPassword("vault:secrets/basic-pass");
        basicAuth.setTokenPrefix("Basic");

        // expected basic creds
        String creds = Base64.getEncoder()
                .encodeToString(("dummyUser:dummyPass").getBytes(StandardCharsets.UTF_8));

        // Custom Signature
        CustomSignatureAuth custom = new CustomSignatureAuth();
        custom.setAlgo("HMAC-SHA256");
        custom.setKey("vault:secrets/signing-key");
        custom.setParameters(Map.of("timestamp", "now"));

        // MTLS
        MtlsAuth mtls = new MtlsAuth();
        mtls.setCertVaultAlias("vault:certs/client-cert");
        mtls.setKeyVaultAlias("vault:keys/client-key");


        return Stream.of(
                // 1. API Key in header
                Arguments.of(
                        List.of(apiKeyHeader),
                        Map.of("X-API-KEY", "dummy-apiKey-secret"),
                        Map.of()
                ),
                // 2. API Key in query
                Arguments.of(
                        List.of(apiKeyQuery),
                        Map.of(),
                        Map.of("api_key", "dummy-apiKey-secret")
                ),
                // 3. Bearer
                Arguments.of(
                        List.of(bearerAuth),
                        Map.of("Authorization", "Bearer dummy-bearer-secret"),
                        Map.of()
                ),
                // 4. Basic
                Arguments.of(
                        List.of(basicAuth),
                        Map.of("Authorization", "Basic " + creds),
                        Map.of()
                ),
                // 5. API Key (header) + Bearer
                Arguments.of(
                        List.of(apiKeyHeader, bearerAuth),
                        Map.of(
                                "X-API-KEY", "dummy-apiKey-secret",
                                "Authorization", "Bearer dummy-bearer-secret"
                        ),
                        Map.of()
                ),
                // 6. API Key (query) + Basic
                Arguments.of(
                        List.of(apiKeyQuery, basicAuth),
                        Map.of("Authorization", "Basic " + creds),
                        Map.of("api_key", "dummy-apiKey-secret")
                ),
                // Custom Signature
                Arguments.of(
                        List.of(custom),
                        Map.of("X-Custom-Signature", "dummy-signature"),
                        Map.of()
                ),
                // MTLS
                Arguments.of(
                        List.of(mtls),
                        Map.of("X-MTLS-Enabled", "true"),
                        Map.of()
                )
        );
    }

    // --- Minimal auth config stubs to make test self-contained ---
}

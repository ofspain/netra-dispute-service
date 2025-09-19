package com.netstra.disputes;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.RestClientExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@SpringBootTest
class DisputeServiceApplicationTests {

	// === 1. GTB Payments (API Key + Static/Dynamic Headers) ===
	@Test
	void testEndpoint1_gtbPayment() {
		EndpointConfig config = new EndpointConfig();
		config.setId(4578L);
		config.setDomainCode("BANK_GTB");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("GTB transaction endpoints");
		config.setDomainOwnerId(234L);

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://api.gtb-bank.com");
		config.setNetwork(network);

		// Security: API Key
		ApiKeyAuth apiKeyAuth = new ApiKeyAuth();
		apiKeyAuth.setHeaderName("X-API-KEY");
		apiKeyAuth.setApiKey("vault:secrets/apiKey-gtb");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail paymentDetail = new EndpointDetail();
		paymentDetail.setUrl("/v1/payments");
		paymentDetail.setMethod(HTTPMethod.POST);
		paymentDetail.setHeaders(List.of(
				new StaticHeader("Authorization", "Bearer ${vault:vk-42}", true),
				new StaticHeader("X-Fixed-Header", "abc123", false)
		));
		paymentDetail.setDynamicHeaders(List.of(
				new DynamicHeader("TransactionRef", true, "Unique transaction id")
		));
		paymentDetail.setRequestBodyTemplate("""
            {
              "id": "${customerId}",
              "transactionAmount": ${amount},
              "currency": "${currency}",
              "note": "${note}"
            }
        """);
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, paymentDetail
		));

		// Resilience
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(3);
		config.setResilience(resilience);

		// Executor usage
		RestClientExecutor executor = null;
		RestClient client = null;
		Map<String, String> pathParams = Map.of();
		Map<String, String> queryParams = Map.of("currency", "USD");
		Map<String, String> headers = Map.of(
				"authToken", "abcd1234",
				"TransactionRef", "TXN-999"
		);
		Map<String, String> bodyContext = Map.of(
				"customerId", "CUST001",
				"amount", "5000",
				"currency", "NGN",
				"note", "Payment for invoice #123"
		);

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeRequest(
				client, config, false, pathParams, queryParams, headers, bodyContext, responseType
		);

		System.out.println("Response (GTB Payment): " + response);
	}

	// === 2. Monnify Wallet Balance (mTLS + Bearer) ===
	@Test
	void testEndpoint2_walletBalance() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("MONNIFY");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Monnify wallet balance check endpoint");

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://secure.bank.com");
		config.setNetwork(network);

		// Security
		MtlsAuth mtls = new MtlsAuth();
		mtls.setCertVaultAlias("vault:certs/my-client");
		mtls.setKeyVaultAlias("vault:keys/my-client");

		BearerTokenAuth bearer = new BearerTokenAuth();
		bearer.setToken("vault:secrets/oauth/xyz-secret");

		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(mtls, bearer));
		config.setSecurity(security);

		// Endpoint
		EndpointDetail detail = new EndpointDetail();
		detail.setUrl("/wallets/{walletId}/balance");
		detail.setMethod(HTTPMethod.GET);
		detail.setPathParamKeys(List.of("walletId"));
		detail.setQueryParamKeys(List.of("currency"));
		detail.setDynamicHeaders(List.of(
				new DynamicHeader("Authorization", true, "Bearer token")
		));

		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		System.out.println("Config (WalletBalance): " + config);
	}

	// === 3. FX Provider Rates (AES Encryption + Fallback) ===
	@Test
	void testEndpoint3_fxRates() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("FX_PROVIDER");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("FX provider exchange rate lookup");

		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("http://192.168.1.20:8080/api");
		config.setNetwork(network);

		// Security with Encryption
		EncryptionConfig enc = new EncryptionConfig();
		enc.setType(EncryptionConfig.EncryptionType.AES);
		enc.setAlgorithm("AES/GCM/NoPadding");
		enc.setEncryptionKey("vault:keys/aes-key");
		enc.setAadHeaders(List.of("Transaction-Id", "Date"));
		SecurityConfig security = new SecurityConfig();
		security.setEncryption(enc);
		config.setSecurity(security);

		EndpointDetail rates = new EndpointDetail();
		rates.setUrl("/api/rates");
		rates.setMethod(HTTPMethod.GET);
		rates.setQueryParamKeys(List.of("baseCurrency", "targetCurrencies"));
		rates.setDynamicHeaders(List.of(new DynamicHeader("x-api-key", true, "API Key")));

		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.BULK_TRANSACTION_SEARCH, rates
		));

		// Fallback
		FallbackConfig fallback = new FallbackConfig();
		fallback.setType(FallbackConfig.FallbackType.STATIC_RESPONSE);
		fallback.setValue("""
            {
              "baseCurrency": "USD",
              "targetCurrencies": ["EUR", "NGN"],
              "rates": { "EUR": 0.9, "NGN": 1500 }
            }
        """);
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.setFallback(fallback);
		config.setResilience(resilience);

		System.out.println("Config (FX Rates): " + config);
	}

	// === 4. StockData (API Key Auth) ===
	@Test
	void testEndpoint4_stockData() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("STOCKDATA");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Stock data provider with API key auth");

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://stockdata.api");
		config.setNetwork(network);

		// Security: API Key
		ApiKeyAuth apiKeyAuth = new ApiKeyAuth();
		apiKeyAuth.setHeaderName("X-API-KEY");
		apiKeyAuth.setApiKeyVaultAlias("vault:keys/stockdata");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth));
		config.setSecurity(security);

		// Endpoint
		EndpointDetail quotesDetail = new EndpointDetail();
		quotesDetail.setUrl("/quotes");
		quotesDetail.setMethod(HTTPMethod.GET);
		quotesDetail.setQueryParamKeys(List.of("symbol"));
		quotesDetail.setDynamicHeaders(List.of(
				new DynamicHeader("X-API-KEY", true, "API key at runtime")
		));

		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, quotesDetail
		));

		System.out.println("Config (StockData): " + config);
	}

	// === 5. Legacy System (Basic Auth + Retry Policy) ===
	@Test
	void testEndpoint5_legacySystem() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("LEGACY_SYS");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Legacy system with Basic Auth and retry policy");

		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://legacy-system.com");
		config.setNetwork(network);

		// Security: Basic Auth
		BasicAuth basicAuth = new BasicAuth();
		basicAuth.setUsername("legacyUser");
		basicAuth.setPassword("vault:passwords/legacy");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(basicAuth));
		config.setSecurity(security);

		// Resilience: Retry
		RetryConfig retry = new RetryConfig();
		retry.setMaxAttempts(5);
		retry.setInitialDelayMillis(500);
		retry.setMultiplier(1.5);
		retry.setMaxDelayMillis(5000);
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.setRetry(retry);
		config.setResilience(resilience);

		// Endpoint
		EndpointDetail reportDetail = new EndpointDetail();
		reportDetail.setUrl("/reports");
		reportDetail.setMethod(HTTPMethod.GET);
		reportDetail.setQueryParamKeys(List.of("date", "type"));
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, reportDetail
		));

		System.out.println("Config (LegacySys): " + config);
	}

	// === 6. Partner API (Proxy + Circuit Breaker) ===
	@Test
	void testEndpoint6_partnerApi() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("PARTNER_API");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Partner API with proxy and circuit breaker");

		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://partner-api.com");
		network.setUseProxy(true);
		ProxyConfig proxy = new ProxyConfig();
		proxy.setHost("proxy.corp.net");
		proxy.setPort(8080);
		proxy.setUsername("svc-proxy");
		proxy.setPassword("vault:proxy/password");
		proxy.setType(ProxyConfig.ProxyType.HTTP);
		network.setProxy(proxy);
		config.setNetwork(network);

		// Resilience: Circuit Breaker
		CircuitBreakerConfig cb = new CircuitBreakerConfig();
		cb.setFailureThreshold(3);
		cb.setResetTimeoutMillis(30000);
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.setCircuitBreaker(cb);
		config.setResilience(resilience);

		// Endpoint
		EndpointDetail ordersDetail = new EndpointDetail();
		ordersDetail.setUrl("/orders");
		ordersDetail.setMethod(HTTPMethod.POST);
		ordersDetail.setRequestBodyTemplate("""
            {
              "orderId": "${orderId}",
              "amount": ${amount},
              "currency": "${currency}"
            }
        """);
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, ordersDetail
		));

		System.out.println("Config (PartnerAPI): " + config);
	}
}

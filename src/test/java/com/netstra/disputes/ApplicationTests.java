package com.netstra.disputes;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.RestClientExecutor;
import com.netstra.disputes.services.client.util.RestClientFactory;
import com.netstra.disputes.services.client.vault.VaultManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
class ApplicationTests {

	@Autowired
	@Qualifier("awsVault")
	private VaultManager vaultManager;

	@Autowired
	private RestClientExecutor executor;

	@Autowired
	private RestClientFactory restClientFactory;

	// === 1. GTB Payments (API Key + Static/Dynamic Headers) ===
	@Test
	void testEndpoint1() {
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
//		apiKeyAuth.setAsQueryParam(true);
		apiKeyAuth.setQueryParamName("api-key");
		apiKeyAuth.setPrefix("pre");
		apiKeyAuth.setPrefixSeparator("<~>");
		apiKeyAuth.setSuffix("@suf");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail paymentDetail = new EndpointDetail();
		paymentDetail.setUrl("/v1/payments");
		paymentDetail.setMethod(EndpointDetail.HTTPMethod.POST);
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
		RestClient client = restClientFactory.buildRestClientUnProxied(30);
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

		List<DynamicHeader> dynamicHeaders = List.of(
				new DynamicHeader("transactionID", true, "unique identifier of the transation"),
				new DynamicHeader("branch", false, "where transaction occurs")
		);
		Map<DynamicHeader,String> dynamicHeaderMap = new HashMap<>();

		for(DynamicHeader dynamicHeader : dynamicHeaders){
			if(dynamicHeader.isRequired()){
				String value = "dummyVal";
				dynamicHeaderMap.put(dynamicHeader, value);
			}
		}

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (GTB Payment): " + response);
	}

	// === 2. Monnify Wallet Balance (mTLS + Bearer) ===
	@Test
	void testEndpoint2() {
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
		detail.setMethod(EndpointDetail.HTTPMethod.GET);
		detail.setPathParamKeys(List.of("walletId"));
		detail.setQueryParamKeys(List.of("currency"));
		detail.setDynamicHeaders(List.of(
				new DynamicHeader("Authorization", true, "Bearer token")
		));


		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		// === Resilience (retry like in desired) ===
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(3);
		config.setResilience(resilience);

		// === Executor + inputs ===
		RestClient client = restClientFactory.buildRestClientUnProxied(30);

		Map<String, String> pathParams = Map.of(
				"walletId", "WALLET-12345"
		);
		Map<String, String> queryParams = Map.of(
				"currency", "NGN"
		);

		List<DynamicHeader> dynamicHeaders = detail.getDynamicHeaders();
		Map<DynamicHeader, String> dynamicHeaderMap = new HashMap<>();
		for (DynamicHeader dynamicHeader : dynamicHeaders) {
			if (dynamicHeader.isRequired()) {
				// in practice, resolve from vault/token service
				String value = "Bearer dummy-token";
				dynamicHeaderMap.put(dynamicHeader, value);
			}
		}

		// For GET no body, but keep empty
		Map<String, String> bodyContext = Map.of();

		ParameterizedTypeReference<Map<String, Object>> responseType =
				new ParameterizedTypeReference<>() {};

		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (Monnify Wallet Balance): " + response);
	}


	// === 3. FX Provider Rates (AES Encryption + Fallback) ===
	@Test
	void testEndpoint3() {
		EndpointConfig config = new EndpointConfig();
		config.setDomainCode("FX_PROVIDER");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("FX provider exchange rate lookup");

		// === Network ===
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("http://192.168.1.20:8080/api");
		config.setNetwork(network);

		// === Security with Encryption ===
		EncryptionConfig enc = new EncryptionConfig();
		enc.setType(EncryptionConfig.EncryptionType.AES);
		enc.setAlgorithm("AES/GCM/NoPadding");
		enc.setEncryptionKey("vault:keys/aes-key");

		Map<String, String> headerMap = Map.of(
				"Transaction-Id", "transREF",          // dynamic
				"Date", "2025-09-20T12:00:00Z"         // static
		);

		List<EncryptionConfig.AadHeader> aadHeaders = headerMap.entrySet().stream()
				.map(entry -> new EncryptionConfig.AadHeader(
						entry.getKey(),
						entry.getValue(),
						false,
						entry.getValue() == null)
				).toList();

		enc.setAadHeaders(aadHeaders);

		// === Security with Signature ===
		CustomSignatureAuth sig = new CustomSignatureAuth();
		sig.setAlgo("HMAC-SHA256");
		sig.setKey("vault:keys/hmac-secret");
		sig.setSignatureName("X-Signature");
		sig.setPlacement(CustomSignatureAuth.SignaturePlacement.HEADER);
		sig.setComponents(List.of(
				CustomSignatureAuth.SignatureComponent.METHOD,
				CustomSignatureAuth.SignatureComponent.PATH,
				CustomSignatureAuth.SignatureComponent.QUERY_STRING,
				CustomSignatureAuth.SignatureComponent.BODY,
				CustomSignatureAuth.SignatureComponent.TIMESTAMP
		));

		// === Encapsulate both in SecurityConfig ===
		SecurityConfig security = new SecurityConfig();
		security.setEncryption(enc);
		security.getAuthConfigs().add(sig);
		config.setSecurity(security);

		// === Endpoint detail ===
		EndpointDetail rates = new EndpointDetail();
		rates.setUrl("/api/rates");
		rates.setMethod(EndpointDetail.HTTPMethod.POST);
		rates.setQueryParamKeys(List.of("baseCurrency", "targetCurrencies"));
		rates.setDynamicHeaders(List.of(
				new DynamicHeader("x-api-key", true, "API Key")
		));

		rates.setRequestBodyTemplate("""
            {
              "id": "${customerId}",
              "transactionAmount": ${amount},
              "currency": "${currency}",
              "note": "${note}"
            }
        """);

		Map<String, String> bodyContext = Map.of(
				"customerId", "CUST001",
				"amount", "5000",
				"currency", "NGN",
				"note", "Payment for invoice #123"
		);

		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.BULK_TRANSACTION_SEARCH, rates
		));

		// === Resilience with Fallback ===
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
		resilience.getRetry().setMaxAttempts(3);
		resilience.setFallback(fallback);
		config.setResilience(resilience);

		// === Executor usage (DESIRABLE part) ===
		RestClient client = restClientFactory.buildRestClientUnProxied(30);

		Map<String, String> pathParams = Map.of(); // none for this endpoint
		Map<String, String> queryParams = new HashMap<>();
		queryParams.put("baseCurrency", "USD");
		queryParams.put("targetCurrencies", "EUR,NGN");

		// Dynamic headers
		List<DynamicHeader> dynamicHeaders = rates.getDynamicHeaders();
		Map<DynamicHeader, String> dynamicHeaderMap = new HashMap<>();
		for (DynamicHeader dynamicHeader : dynamicHeaders) {
			if (dynamicHeader.isRequired()) {
				dynamicHeaderMap.put(dynamicHeader, "dummy-api-key");
			}
		}


		ParameterizedTypeReference<Map<String, Object>> responseType =
				new ParameterizedTypeReference<>() {};

		Map<String, Object> response = executor.executeMultipleTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (FX Rates): " + response);
	}



	// === 4. StockData (API Key Auth) ===
	@Test
	void testEndpoint4() {
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
		apiKeyAuth.setApiKey("vault:keys/stockdata");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth));
		config.setSecurity(security);

		// Endpoint
		EndpointDetail quotesDetail = new EndpointDetail();
		quotesDetail.setUrl("/quotes");
		quotesDetail.setMethod(EndpointDetail.HTTPMethod.GET);
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
	void testEndpoint5() {
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
		reportDetail.setMethod(EndpointDetail.HTTPMethod.GET);
		reportDetail.setQueryParamKeys(List.of("date", "type"));
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, reportDetail
		));

		System.out.println("Config (LegacySys): " + config);
	}

	// === 6. Partner API (Proxy + Circuit Breaker) ===
	@Test
	void testEndpoint6() {
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
		ordersDetail.setMethod(EndpointDetail.HTTPMethod.POST);
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


	@Test
	void testEndpointWithBearerTokenAuth() {
		EndpointConfig config = new EndpointConfig();
		config.setId(7851L);
		config.setDomainCode("PAYPAL");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("PayPal transaction endpoints");
		config.setDomainOwnerId(300L);

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://api.paypal.com");
		config.setNetwork(network);

		// Security: Bearer
		BearerTokenAuth bearerAuth = new BearerTokenAuth();
		bearerAuth.setHeaderName("Authorization");
		bearerAuth.setToken("vault:secrets/bearer-paypal");
		bearerAuth.setPrefix("Bearer");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(bearerAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail detail = new EndpointDetail();
		detail.setUrl("/v1/payments");
		detail.setMethod(EndpointDetail.HTTPMethod.POST);
		detail.setHeaders(List.of(
				new StaticHeader("X-Client", "paypal-client", true)
		));
		detail.setRequestBodyTemplate("""
        {
          "payer_id": "${payerId}",
          "amount": ${amount},
          "currency": "${currency}"
        }
    """);
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		// Resilience
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(3);
		config.setResilience(resilience);

		// Executor usage
		RestClient client = restClientFactory.buildRestClientUnProxied(30);
		Map<String, String> pathParams = Map.of();
		Map<String, String> queryParams = Map.of("currency", "USD");
		Map<DynamicHeader,String> dynamicHeaderMap = Map.of();

		Map<String, String> bodyContext = Map.of(
				"payerId", "PAYER-007",
				"amount", "1200",
				"currency", "USD"
		);

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (PayPal Payment): " + response);
	}


	@Test
	void testEndpointWithBasicAuth() {
		EndpointConfig config = new EndpointConfig();
		config.setId(9902L);
		config.setDomainCode("SHOPIFY");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Shopify order endpoints");
		config.setDomainOwnerId(400L);

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://api.shopify.com");
		config.setNetwork(network);

		// Security: Basic
		BasicAuth basicAuth = new BasicAuth();
		basicAuth.setHeaderName("Authorization");
		basicAuth.setUsername("vault:secrets/shopify-username");
		basicAuth.setPassword("vault:secrets/shopify-password");
		basicAuth.setTokenPrefix("Basic");
		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(basicAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail detail = new EndpointDetail();
		detail.setUrl("/v1/orders");
		detail.setMethod(EndpointDetail.HTTPMethod.GET);
		detail.setHeaders(List.of(
				new StaticHeader("X-Client", "shopify-client", true)
		));
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		// Resilience
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(2);
		config.setResilience(resilience);

		// Executor usage
		RestClient client = restClientFactory.buildRestClientUnProxied(30);
		Map<String, String> pathParams = Map.of();
		Map<String, String> queryParams = Map.of("status", "open");
		Map<DynamicHeader,String> dynamicHeaderMap = Map.of();

		Map<String, String> bodyContext = Map.of();

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (Shopify Orders): " + response);
	}


	@Test
	void testEndpointWithApiKeyAndBearerAuth() {
		EndpointConfig config = new EndpointConfig();
		config.setId(7781L);
		config.setDomainCode("STRIPE");
		config.setDomainType(DomainType.FINANCIAL_INSTITUTION);
		config.setDescription("Stripe payment endpoints");
		config.setDomainOwnerId(500L);

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://api.stripe.com");
		config.setNetwork(network);

		// Security: API Key + Bearer
		ApiKeyAuth apiKeyAuth = new ApiKeyAuth();
		apiKeyAuth.setHeaderName("X-API-KEY");
		apiKeyAuth.setApiKey("vault:secrets/stripe-api-key");

		BearerTokenAuth bearerAuth = new BearerTokenAuth();
		bearerAuth.setHeaderName("Authorization");
		bearerAuth.setToken("vault:secrets/stripe-bearer-token");
		bearerAuth.setPrefix("Bearer");

		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth, bearerAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail detail = new EndpointDetail();
		detail.setUrl("/v1/charges");
		detail.setMethod(EndpointDetail.HTTPMethod.POST);
		detail.setHeaders(List.of(
				new StaticHeader("X-Client", "stripe-client", true)
		));
		detail.setRequestBodyTemplate("""
        {
          "customer": "${customerId}",
          "amount": ${amount},
          "currency": "${currency}"
        }
    """);
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		// Resilience
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(3);
		config.setResilience(resilience);

		// Executor usage
		RestClient client = restClientFactory.buildRestClientUnProxied(30);
		Map<String, String> pathParams = Map.of();
		Map<String, String> queryParams = Map.of();
		Map<DynamicHeader,String> dynamicHeaderMap = Map.of();

		Map<String, String> bodyContext = Map.of(
				"customerId", "CUST-9001",
				"amount", "2500",
				"currency", "USD"
		);

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (Stripe Charge): " + response);
	}

	@Test
	void testEndpointWithApiKeyAndBasicAuth() {
		EndpointConfig config = new EndpointConfig();
		config.setId(8851L);
		config.setDomainCode("SALESFORCE");
		config.setDomainType(DomainType.SWITCH);
		config.setDescription("Salesforce API endpoints");
		config.setDomainOwnerId(600L);

		// Network
		NetworkConfig network = new NetworkConfig();
		network.setBaseUrl("https://api.salesforce.com");
		config.setNetwork(network);

		// Security: API Key + Basic
		ApiKeyAuth apiKeyAuth = new ApiKeyAuth();
		apiKeyAuth.setHeaderName("X-API-KEY");
		apiKeyAuth.setApiKey("vault:secrets/salesforce-api-key");

		BasicAuth basicAuth = new BasicAuth();
		basicAuth.setHeaderName("Authorization");
		basicAuth.setUsername("vault:secrets/salesforce-username");
		basicAuth.setPassword("vault:secrets/salesforce-password");
		basicAuth.setTokenPrefix("Basic");

		SecurityConfig security = new SecurityConfig();
		security.setAuthConfigs(List.of(apiKeyAuth, basicAuth));
		config.setSecurity(security);

		// Endpoint detail
		EndpointDetail detail = new EndpointDetail();
		detail.setUrl("/v1/accounts");
		detail.setMethod(EndpointDetail.HTTPMethod.GET);
		detail.setHeaders(List.of(
				new StaticHeader("X-Client", "salesforce-client", true)
		));
		config.setEndpoints(Map.of(
				EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH, detail
		));

		// Resilience
		ResilienceConfig resilience = new ResilienceConfig();
		resilience.getRetry().setMaxAttempts(2);
		config.setResilience(resilience);

		// Executor usage
		RestClient client = restClientFactory.buildRestClientUnProxied(30);
		Map<String, String> pathParams = Map.of();
		Map<String, String> queryParams = Map.of("active", "true");
		Map<DynamicHeader,String> dynamicHeaderMap = Map.of();

		Map<String, String> bodyContext = Map.of();

		ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<>() {};
		Map<String, Object> response = executor.executeUniqueTransactionRequest(
				client, config, pathParams, queryParams, dynamicHeaderMap, bodyContext, responseType
		);

		System.out.println("Response (Salesforce Accounts): " + response);
	}


}

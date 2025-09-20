package com.netstra.disputes.services.client.util;

import com.netra.commons.trace.CallOperation;
import org.springframework.http.HttpMethod;

/**
 * Execution context for better tracing and debugging
 */
public class ExecutionContext {
    private final HttpMethod method;
    private final String url;
    private final CallOperation operation;
    private final String userName;
    private final long startTime;

    private ExecutionContext(HttpMethod method, String url, CallOperation operation,
                             String userName, long startTime) {
        this.method = method;
        this.url = url;
        this.operation = operation;
        this.userName = userName;
        this.startTime = startTime;
    }

    public static ExecutionContextBuilder builder() {
        return new ExecutionContextBuilder();
    }

    // Getters
    public HttpMethod getMethod() { return method; }
    public String getUrl() { return url; }
    public CallOperation getOperation() { return operation; }
    public String getUserName() { return userName; }
    public long getStartTime() { return startTime; }

    public static class ExecutionContextBuilder {
        private HttpMethod method;
        private String url;
        private CallOperation operation;
        private String userName;
        private long startTime;

        public ExecutionContextBuilder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public ExecutionContextBuilder url(String url) {
            this.url = url;
            return this;
        }

        public ExecutionContextBuilder operation(CallOperation operation) {
            this.operation = operation;
            return this;
        }

        public ExecutionContextBuilder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public ExecutionContextBuilder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(method, url, operation, userName, startTime);
        }
    }
}

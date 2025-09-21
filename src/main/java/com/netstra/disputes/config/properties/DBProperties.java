package com.netstra.disputes.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource")
public class DBProperties {

    private String url;
    private String username;
    private String password;
    private Hikari hikari = new Hikari();

    public static class Hikari {
        private int maximumPoolSize = 10;
        private long connectionTimeout = 30000;
        private long idleTimeout = 300000;
        private long maxLifetime = 1800000;
        private long leakDetectionThreshold = 30000;

        // getters & setters
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }
        public long getLeakDetectionThreshold() { return leakDetectionThreshold; }
        public void setLeakDetectionThreshold(long leakDetectionThreshold) { this.leakDetectionThreshold = leakDetectionThreshold; }
    }

    // getters & setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Hikari getHikari() { return hikari; }
    public void setHikari(Hikari hikari) { this.hikari = hikari; }
}


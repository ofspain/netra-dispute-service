package com.netstra.disputes.config;


import com.netstra.disputes.config.properties.DBProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DBProperties.class)
public class DBConfig {

    @Bean
    @Primary
    public DataSource devDataSource(DBProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(props.getUrl());
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());

        ds.setMaximumPoolSize(props.getHikari().getMaximumPoolSize());
        ds.setConnectionTimeout(props.getHikari().getConnectionTimeout());
        ds.setIdleTimeout(props.getHikari().getIdleTimeout());
        ds.setMaxLifetime(props.getHikari().getMaxLifetime());
        ds.setLeakDetectionThreshold(props.getHikari().getLeakDetectionThreshold());

        return ds;
    }

    @Bean
    @Profile("prod")
    public DataSource prodDataSource(DBProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("software.amazon.jdbc.Driver");

        // Append wrapper + plugins (failover + read/write splitting)
        String jdbcUrl = "jdbc:aws-wrapper:" + props.getUrl() +
                "?wrapperPlugins=failover,readWriteSplitting" +
                "&readWriteSplitting.pinWriterConnectionUntilTransactionEnd=true" +
                "&readWriteSplitting.writeOperationDetectionEnabled=true";

        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());

        ds.setMaximumPoolSize(props.getHikari().getMaximumPoolSize());
        ds.setConnectionTimeout(props.getHikari().getConnectionTimeout());
        ds.setIdleTimeout(props.getHikari().getIdleTimeout());
        ds.setMaxLifetime(props.getHikari().getMaxLifetime());
        ds.setLeakDetectionThreshold(props.getHikari().getLeakDetectionThreshold());

        return ds;
    }
}

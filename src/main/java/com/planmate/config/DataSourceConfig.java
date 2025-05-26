package com.planmate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
// No hibernate5 imports
// No @EnableTransactionManagement import

import jakarta.sql.DataSource;
// No java.util.Properties import if hibernateProperties method is removed

@Configuration
// No @EnableTransactionManagement annotation
public class DataSourceConfig {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;
    
    @Value("${spring.datasource.url.localenv}")
    private String urlLocalEnv;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    // These properties would be used by Spring Boot auto-configuration
    // if present in application.properties (e.g., spring.jpa.properties.hibernate.dialect)
    // They are no longer directly used by this Java config class.
    // @Value("${spring.jpa.properties.hibernate.dialect}")
    // private String hibernateDialect;
    //
    // @Value("${spring.jpa.show-sql}")
    // private String hibernateShowSql;
    //
    // @Value("${hibernate.hbm2ddl.auto}")
    // private String hibernateHbm2ddlAuto;

    @Value("${spring.application.environment}")
    private String environment;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        if ("local".equalsIgnoreCase(environment)) {
        	dataSource.setUrl(urlLocalEnv);
        } else {
        	dataSource.setUrl(url);
        }
        System.out.println("Using data source url: " + dataSource.getUrl());
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    // Manual sessionFactory bean removed
    // Manual transactionManager bean removed
    // Manual hibernateProperties method removed
}

package com.planmate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
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

    @Value("${spring.jpa.properties.hibernate.dialect}")
    private String hibernateDialect;

    @Value("${spring.jpa.show-sql}")
    private String hibernateShowSql;

    @Value("${hibernate.hbm2ddl.auto}")
    private String hibernateHbm2ddlAuto;

    @Value("${spring.application.environment}")
    private String environment;

    @Bean
    public LocalSessionFactoryBean sessionFactory() {
        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
        sessionFactory.setDataSource(dataSource());
        sessionFactory.setPackagesToScan("com.planmate.dto");
        sessionFactory.setHibernateProperties(hibernateProperties());
        return sessionFactory;
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        if ("local".equalsIgnoreCase(environment)) {
        	dataSource.setUrl(urlLocalEnv);
        } else {
        	dataSource.setUrl(url);
        }
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    public HibernateTransactionManager transactionManager() {
        HibernateTransactionManager txManager = new HibernateTransactionManager();
        txManager.setSessionFactory(sessionFactory().getObject());
        return txManager;
    }

    private Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", hibernateDialect);
        properties.put("hibernate.show_sql", hibernateShowSql);
        properties.put("hibernate.hbm2ddl.auto", hibernateHbm2ddlAuto);
        return properties;
    }
}

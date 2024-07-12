package com.planmate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
@RestController
public class PlanmateApplication extends SpringBootServletInitializer {

    private static final Logger logger = LoggerFactory.getLogger(PlanmateApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PlanmateApplication.class, args);
        logger.info("Planmate started successfully.");
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(PlanmateApplication.class);
    }

    @RequestMapping(value = "/hello")
    public String helloWorld() {
    	return "hello world";
    }
}

package com.planmate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${application.security.disabled:false}")
    private boolean securityDisabled;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (securityDisabled) {
            http.authorizeRequests()
                .anyRequest().permitAll()
                .and().csrf().disable();
        } else {
            http.authorizeRequests()
                .antMatchers("/planmate/hello").permitAll()  // Enable /planmate/hello endpoint
                .anyRequest().authenticated()
                .and().formLogin().permitAll()
                .and().logout().permitAll();
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

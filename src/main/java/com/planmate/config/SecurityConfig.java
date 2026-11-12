package com.planmate.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Value("${application.security.disabled:false}")
	private boolean securityDisabled;

	/** Origins allowed to call the API from a browser. Never "*": credentials are allowed (see corsFilter). */
	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Autowired
	private AuthRequestFilter authRequestFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		if (securityDisabled) {
			http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
					.csrf(csrf -> csrf.disable());
		} else {
			http.cors(Customizer.withDefaults())
					.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(auth -> auth
							// Order matters: the first matching rule wins.
							.requestMatchers("/api/v1/auth/password").authenticated()
							.requestMatchers("/api/v1/auth/**", "/hello", "/api/health", "/error", "/swagger-ui/**", "/api-docs/**").permitAll()
							// Profile pictures are loaded by img tags (no token); the unguessable key in the URL protects them
							.requestMatchers(HttpMethod.GET, "/api/v1/avatars/*/*").permitAll()
							// Calendar subscriptions are fetched by calendar apps (no token); the secret key in the URL protects them
							.requestMatchers(HttpMethod.GET, "/api/v2/calendar/*").permitAll()
							.requestMatchers("/api/v2/events/merge", "/api/v2/events/*/attendance", "/api/v2/events/*/response").authenticated()
							// Remaining event endpoints serve guests too; EventMongoController enforces ownership.
							.requestMatchers("/api/v2/events/**").permitAll()
							.anyRequest().authenticated())
					.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.addFilterBefore(authRequestFilter, UsernamePasswordAuthenticationFilter.class);
		}
		return http.build();
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring().requestMatchers("/api/health", "/api/health/**", "/error", "/swagger-ui/**",
				"/api-docs/**");
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
			throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public CorsFilter corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		// Credentials are allowed, so the origin list must be explicit — never "*", which would let any site
		// make credentialed calls and read the responses.
		config.setAllowCredentials(true);
		config.setAllowedOrigins(allowedOrigins);
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}
}

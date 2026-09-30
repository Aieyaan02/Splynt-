package com.aieyaan.splynt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))

                .authorizeHttpRequests(authorize -> authorize
                        /*
                         * React application files must be publicly accessible.
                         * Authentication is handled by the React login page.
                         */
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.svg",
                                "/icons.svg",
                                "/error"
                        )
                        .permitAll()

                        /*
                         * Registration and login must remain public because
                         * users do not have a JWT before authenticating.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/public/inquiries",
                                "/api/auth/register",
                                "/api/auth/login"
                        )
                        .permitAll()

                        /*
                         * Clover must access this endpoint before a Splynt
                         * JWT exists.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/integrations/clover/connect"
                        )
                        .permitAll()

                        /*
                         * Health checks are used by Docker and AWS.
                         * Swagger remains accessible during development.
                         */
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        /*
                         * Every other API request requires a valid JWT.
                         */
                        .anyRequest()
                        .authenticated())

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }
}
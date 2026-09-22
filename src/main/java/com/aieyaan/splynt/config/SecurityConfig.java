package com.aieyaan.splynt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**"))

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register"
                        )
                        .permitAll()

                        .requestMatchers("/actuator/health")
                        .permitAll()

                        .anyRequest()
                        .authenticated())

                .formLogin(form -> form.permitAll())

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
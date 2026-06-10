package com.resumetailor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/register",
                    "/forgot-password", "/reset-password", "/reset-password-success",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico",
                    // Stripe webhook must be publicly accessible (called by Stripe servers)
                    "/api/webhook/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")

                .usernameParameter("email")
                .passwordParameter("password")

                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )

            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )

            .csrf(csrf -> csrf
                // manter CSRF ativo, mas ignorar especificamente o endpoint de webhook
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/webhook/**"))
            );

        return http.build();
    }
}
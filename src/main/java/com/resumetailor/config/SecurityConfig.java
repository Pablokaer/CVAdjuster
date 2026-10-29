package com.resumetailor.config;

import com.resumetailor.ratelimit.ClientIpResolver;
import com.resumetailor.ratelimit.RateLimitFilter;
import com.resumetailor.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/register", "/about",
                    "/forgot-password", "/reset-password", "/reset-password-success",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico", "/favicon.png",
                    // Error pages, including the 429 page for rate-limited anonymous requests
                    "/error",
                    // Stripe webhook must be publicly accessible (called by Stripe servers)
                    "/api/webhook/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            // After the session's user is loaded, before form login runs
            .addFilterBefore(new RateLimitFilter(rateLimiter, clientIpResolver),
                    UsernamePasswordAuthenticationFilter.class)

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")

                .usernameParameter("email")
                .passwordParameter("password")

                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )

            .csrf(csrf -> csrf
                // manter CSRF ativo, mas ignorar especificamente o endpoint de webhook
                .ignoringRequestMatchers("/api/webhook/**")
            );

        return http.build();
    }
}

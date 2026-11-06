package com.resumetailor.config;

import com.resumetailor.ratelimit.ClientIpResolver;
import com.resumetailor.ratelimit.RateLimitFilter;
import com.resumetailor.ratelimit.RateLimiter;
import com.resumetailor.web.RouteAwareAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final ListableBeanFactory beanFactory;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/register", "/about",
                    "/forgot-password", "/reset-password", "/reset-password-success",
                    "/privacy", "/delete-account",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico", "/favicon.png",
                    // Installable app: manifest, service worker, icons, offline page, Android asset links
                    "/manifest.webmanifest", "/sw.js", "/offline.html", "/icons/**",
                    "/.well-known/assetlinks.json",
                    // Files for search engines and AI agents
                    "/robots.txt", "/sitemap.xml", "/llms.txt", "/openapi.json",
                    // Error pages, including the 429 page for rate-limited anonymous requests
                    "/error",
                    // Stripe webhook must be publicly accessible (called by Stripe servers)
                    "/api/webhook/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            // Login always lands on "/" (defaultSuccessUrl below), so the saved request was never
            // used; not saving it keeps anonymous 404s and API 401s from creating sessions.
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))

            .exceptionHandling(exceptions -> exceptions
                // Unknown paths get a 404 and the API a 401 instead of a redirect to the login form
                .authenticationEntryPoint(new RouteAwareAuthenticationEntryPoint(beanFactory, "/login"))
                // Keep the exception so the error page can tell a CSRF failure apart
                .accessDeniedHandler((request, response, denied) -> {
                    request.setAttribute(WebAttributes.ACCESS_DENIED_403, denied);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                })
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

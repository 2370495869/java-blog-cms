package com.blogcms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/articles/**", "/login", "/css/**", "/error", "/actuator/health").permitAll()
                        .requestMatchers("/manage/users/**", "/manage/taxonomy/**").hasRole("ADMIN")
                        .requestMatchers("/manage/review/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers("/manage/**").hasAnyRole("AUTHOR", "EDITOR", "ADMIN")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/manage", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' https:; style-src 'self'; " +
                                "script-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}

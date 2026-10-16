package zas.admin.zia.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * With zas-common-security-starter on the classpath ({@code -Pinternal}), every request requires a Blue token
 * except actuator and OpenAPI/Swagger endpoints. Without it, every request is permitted.
 */
@Configuration
public class SecurityConfig {

    static final String BLUE_GATEWAY_SECURITY_CONFIG = "ch.admin.zas.common.security.config.BlueGatewayJwtSecurityConfig";

    private static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(0)
    @ConditionalOnClass(name = BLUE_GATEWAY_SECURITY_CONFIG)
    SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .securityMatcher(PUBLIC_PATHS)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(1)
    @ConditionalOnMissingClass(BLUE_GATEWAY_SECURITY_CONFIG)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

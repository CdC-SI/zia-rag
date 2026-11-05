package zas.admin.zia.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables {@code @PreAuthorize} checks only when the ZAS security library is present,
 * since it is the one authenticating callers (Blue token). Without it, callers are anonymous.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnClass(name = SecurityConfig.BLUE_GATEWAY_SECURITY_CONFIG)
class MethodSecurityConfig {
}

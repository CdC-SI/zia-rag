package zas.admin.zia.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Grants access to callers holding at least one of the configured RHDS groups (full DNs).
 * Used from {@code @PreAuthorize("@retrieverAccess.isGranted(authentication)")}.
 * <p>
 * Groups are separated by {@code ;} since DNs themselves contain commas.
 * DNs are compared as LDAP names (case-insensitive, whitespace-tolerant).
 * An empty configuration denies everyone.
 */
@Component(RetrieverAccess.BEAN_NAME)
public class RetrieverAccess {

    public static final String BEAN_NAME = "retrieverAccess";
    public static final String PRE_AUTHORIZE_EXPRESSION = "@" + BEAN_NAME + ".isGranted(authentication)";

    private static final Logger LOG = LoggerFactory.getLogger(RetrieverAccess.class);
    private static final String SEPARATOR = ";";

    private final Set<LdapName> allowedGroups;

    RetrieverAccess(@Value("${zia.security.retriever-groups:}") String retrieverGroups) {
        this.allowedGroups = parseConfiguredGroups(retrieverGroups);
        if (allowedGroups.isEmpty()) {
            LOG.warn("No retriever group configured (zia.security.retriever-groups): all API calls will be denied");
        } else {
            LOG.info("Retriever access granted to groups: {}", allowedGroups);
        }
    }

    public boolean isGranted(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || allowedGroups.isEmpty()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(RetrieverAccess::toLdapName)
                .flatMap(Optional::stream)
                .anyMatch(allowedGroups::contains);
    }

    private static Set<LdapName> parseConfiguredGroups(String retrieverGroups) {
        if (retrieverGroups == null || retrieverGroups.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(retrieverGroups.split(SEPARATOR))
                .map(String::strip)
                .filter(group -> !group.isEmpty())
                .map(group -> toLdapName(group).orElseThrow(() -> new IllegalArgumentException(
                        "Invalid DN in zia.security.retriever-groups: '%s'".formatted(group))))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<LdapName> toLdapName(String value) {
        try {
            LdapName name = new LdapName(value);
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        } catch (InvalidNameException e) {
            return Optional.empty();
        }
    }
}

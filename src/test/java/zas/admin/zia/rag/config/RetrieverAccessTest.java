package zas.admin.zia.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RetrieverAccessTest {

    private static final String RETRIEVER =
            "cn=RETRIEVER,ou=DEV,ou=MyApp,ou=Applications,ou=Groups,dc=example,dc=org";
    private static final String ADMIN =
            "cn=ADMIN,ou=DEV,ou=MyApp,ou=Applications,ou=Groups,dc=example,dc=org";

    private final RetrieverAccess access = new RetrieverAccess(RETRIEVER + " ; " + ADMIN + ";");

    @Test
    void grantsCallerHoldingAnyConfiguredGroup() {
        assertThat(access.isGranted(authenticated("ROLE_USER", RETRIEVER))).isTrue();
        assertThat(access.isGranted(authenticated(ADMIN))).isTrue();
    }

    @Test
    void comparesDnsCaseAndWhitespaceInsensitively() {
        String variant = "CN=retriever, OU=dev, ou=MyApp, ou=Applications, ou=Groups, dc=EXAMPLE, dc=org";

        assertThat(access.isGranted(authenticated(variant))).isTrue();
    }

    @Test
    void deniesSameCnInAnotherBranch() {
        assertThat(access.isGranted(authenticated(RETRIEVER.replace("ou=DEV", "ou=TEST")))).isFalse();
        assertThat(access.isGranted(authenticated("cn=RETRIEVER"))).isFalse();
    }

    @Test
    void ignoresNonDnAuthorities() {
        assertThat(access.isGranted(authenticated("ROLE_RETRIEVER", "RETRIEVER", "not a dn"))).isFalse();
    }

    @Test
    void deniesMissingOrAnonymousOrUnauthenticatedCaller() {
        Authentication unauthenticated = new TestingAuthenticationToken("jdoe", "n/a", AuthorityUtils.createAuthorityList(RETRIEVER));
        unauthenticated.setAuthenticated(false);

        assertThat(access.isGranted(null)).isFalse();
        assertThat(access.isGranted(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))).isFalse();
        assertThat(access.isGranted(unauthenticated)).isFalse();
    }

    @Test
    void emptyConfigurationDeniesEveryone() {
        assertThat(new RetrieverAccess("").isGranted(authenticated(RETRIEVER))).isFalse();
        assertThat(new RetrieverAccess(" ; ").isGranted(authenticated(RETRIEVER))).isFalse();
        assertThat(new RetrieverAccess(null).isGranted(authenticated(RETRIEVER))).isFalse();
    }

    @Test
    void invalidConfiguredDnFailsFast() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetrieverAccess(RETRIEVER + ";RETRIEVER"))
                .withMessageContaining("RETRIEVER");
    }

    private static Authentication authenticated(String... authorities) {
        return new TestingAuthenticationToken("jdoe", "n/a", authorities);
    }
}

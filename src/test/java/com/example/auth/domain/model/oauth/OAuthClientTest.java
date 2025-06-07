package com.example.auth.domain.model.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OAuthClient Domain Model Tests")
class OAuthClientTest {

    private static final String VALID_CLIENT_ID = "test-client-1";
    private static final String VALID_SECRET_HASH = "$argon2id$v=19$m=65536,t=3,p=4$example";
    private static final List<String> VALID_SCOPES = List.of("read", "write");
    private static final Set<String> VALID_GRANT_TYPES = Set.of("client_credentials");
    private static final Integer VALID_EXPIRATION = 3600;
    private static final String VALID_DESCRIPTION = "Test Client";

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create valid OAuth client with all parameters")
        void shouldCreateValidOAuthClient() {
            OAuthClient client = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );

            assertThat(client.getClientId()).isEqualTo(VALID_CLIENT_ID);
            assertThat(client.getClientSecretHash()).isEqualTo(VALID_SECRET_HASH);
            assertThat(client.getStatus()).isEqualTo(ClientStatus.ACTIVE);
            assertThat(client.getAllowedScopes()).containsExactlyElementsOf(VALID_SCOPES);
            assertThat(client.getAllowedGrantTypes()).containsExactlyElementsOf(VALID_GRANT_TYPES);
            assertThat(client.getTokenExpirationSeconds()).isEqualTo(VALID_EXPIRATION);
            assertThat(client.getDescription()).isEqualTo(VALID_DESCRIPTION);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank client ID")
        void shouldRejectInvalidClientId(String clientId) {
            assertThatThrownBy(() -> new OAuthClient(
                clientId,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Client ID cannot be blank");
        }

        @Test
        @DisplayName("Should allow valid scopes")
        void shouldAllowValidScopes() {
            OAuthClient client = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );
            
            assertThat(client.isScopeAllowed("read")).isTrue();
            assertThat(client.isScopeAllowed("write")).isTrue();
            assertThat(client.isScopeAllowed("admin")).isFalse();
        }

        @Test
        @DisplayName("Should handle effective token expiration")
        void shouldHandleEffectiveTokenExpiration() {
            OAuthClient client = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );
            
            assertThat(client.getEffectiveTokenExpiration(1800, 7200)).isEqualTo(1800);
            assertThat(client.getEffectiveTokenExpiration(null, 7200)).isEqualTo(VALID_EXPIRATION);
            assertThat(client.getEffectiveTokenExpiration(10000, 7200)).isEqualTo(7200);
        }

        @Test
        @DisplayName("Should check client status")
        void shouldCheckClientStatus() {
            OAuthClient activeClient = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );
            
            OAuthClient disabledClient = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.DISABLED,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );
            
            assertThat(activeClient.isActive()).isTrue();
            assertThat(disabledClient.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should support grant types")
        void shouldSupportGrantTypes() {
            OAuthClient client = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );
            
            assertThat(client.isGrantTypeSupported("client_credentials")).isTrue();
            assertThat(client.isGrantTypeSupported("authorization_code")).isFalse();
        }

        @Test
        @DisplayName("Should handle equality correctly")
        void shouldHandleEqualityCorrectly() {
            OAuthClient client1 = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );

            OAuthClient client2 = new OAuthClient(
                VALID_CLIENT_ID,
                "different-hash",
                ClientStatus.DISABLED,
                List.of("admin"),
                Set.of("client_credentials"),
                7200,
                "Different description"
            );

            assertThat(client1).isEqualTo(client2);
            assertThat(client1.hashCode()).isEqualTo(client2.hashCode());
        }

        @Test
        @DisplayName("Should not expose sensitive information in toString")
        void shouldNotExposeSensitiveInformation() {
            OAuthClient client = new OAuthClient(
                VALID_CLIENT_ID,
                VALID_SECRET_HASH,
                ClientStatus.ACTIVE,
                VALID_SCOPES,
                VALID_GRANT_TYPES,
                VALID_EXPIRATION,
                VALID_DESCRIPTION
            );

            String toString = client.toString();
            
            assertThat(toString).contains(VALID_CLIENT_ID);
            assertThat(toString).contains("Active");
            assertThat(toString).doesNotContain(VALID_SECRET_HASH);
        }
    }
} 
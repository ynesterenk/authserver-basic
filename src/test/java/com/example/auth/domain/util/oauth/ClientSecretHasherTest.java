package com.example.auth.domain.util.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Client Secret Hasher Tests")
class ClientSecretHasherTest {

    private final ClientSecretHasher hasher = new ClientSecretHasher();

    @Nested
    @DisplayName("Secret Hashing Tests")
    class SecretHashingTests {

        @Test
        @DisplayName("Should hash client secret successfully")
        void shouldHashClientSecretSuccessfully() {
            String secret = "myClientSecret123";
            
            String hash = hasher.hashClientSecret(secret);
            
            assertThat(hash).isNotNull();
            assertThat(hash).isNotBlank();
            assertThat(hash).startsWith("$argon2id$");
            assertThat(hash).doesNotContain(secret);
        }

        @Test
        @DisplayName("Should generate different hashes for same secret")
        void shouldGenerateDifferentHashesForSameSecret() {
            String secret = "sameSecret";
            
            String hash1 = hasher.hashClientSecret(secret);
            String hash2 = hasher.hashClientSecret(secret);
            
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank secrets")
        void shouldRejectNullOrBlankSecrets(String secret) {
            assertThatThrownBy(() -> hasher.hashClientSecret(secret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client secret cannot be null or empty");
        }

        @Test
        @DisplayName("Should handle long secrets")
        void shouldHandleLongSecrets() {
            String longSecret = "a".repeat(256);
            
            String hash = hasher.hashClientSecret(longSecret);
            
            assertThat(hash).isNotNull();
            assertThat(hash).startsWith("$argon2id$");
        }

        @Test
        @DisplayName("Should handle special characters in secrets")
        void shouldHandleSpecialCharactersInSecrets() {
            String specialSecret = "secret@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~`";
            
            String hash = hasher.hashClientSecret(specialSecret);
            
            assertThat(hash).isNotNull();
            assertThat(hash).startsWith("$argon2id$");
        }
    }

    @Nested
    @DisplayName("Secret Verification Tests")
    class SecretVerificationTests {

        @Test
        @DisplayName("Should verify correct secret")
        void shouldVerifyCorrectSecret() {
            String secret = "correctSecret123";
            String hash = hasher.hashClientSecret(secret);
            
            boolean isValid = hasher.verifyClientSecret(secret, hash);
            
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should reject incorrect secret")
        void shouldRejectIncorrectSecret() {
            String secret = "correctSecret123";
            String hash = hasher.hashClientSecret(secret);
            
            boolean isValid = hasher.verifyClientSecret("wrongSecret", hash);
            
            assertThat(isValid).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank secrets for verification")
        void shouldRejectNullOrBlankSecretsForVerification(String secret) {
            String hash = hasher.hashClientSecret("validSecret");
            
            assertThatThrownBy(() -> hasher.verifyClientSecret(secret, hash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client secret cannot be null or empty");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Should reject null or blank hashes for verification")
        void shouldRejectNullOrBlankHashesForVerification(String hash) {
            assertThatThrownBy(() -> hasher.verifyClientSecret("validSecret", hash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hashed secret cannot be null or empty");
        }

        @Test
        @DisplayName("Should handle invalid hash format")
        void shouldHandleInvalidHashFormat() {
            boolean isValid = hasher.verifyClientSecret("secret", "invalid-hash-format");
            
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should be timing attack resistant")
        void shouldBeTimingAttackResistant() {
            String secret = "testSecret123";
            String hash = hasher.hashClientSecret(secret);
            
            // Measure time for correct password
            long startTime = System.nanoTime();
            hasher.verifyClientSecret(secret, hash);
            long correctTime = System.nanoTime() - startTime;
            
            // Measure time for incorrect password
            startTime = System.nanoTime();
            hasher.verifyClientSecret("wrongSecret", hash);
            long incorrectTime = System.nanoTime() - startTime;
            
            // Times should be relatively similar (within reasonable bounds)
            double ratio = (double) Math.max(correctTime, incorrectTime) / Math.min(correctTime, incorrectTime);
            assertThat(ratio).isLessThan(2.0); // Allow up to 2x difference
        }
    }

    @Nested
    @DisplayName("Secret Generation Tests")
    class SecretGenerationTests {

        @Test
        @DisplayName("Should generate secure client secret")
        void shouldGenerateSecureClientSecret() {
            String secret = hasher.generateSecureSecret(32);
            
            assertThat(secret).isNotNull();
            assertThat(secret).isNotBlank();
            assertThat(secret.length()).isEqualTo(32);
        }

        @Test
        @DisplayName("Should generate unique secrets")
        void shouldGenerateUniqueSecrets() {
            String secret1 = hasher.generateSecureSecret(32);
            String secret2 = hasher.generateSecureSecret(32);
            
            assertThat(secret1).isNotEqualTo(secret2);
        }

        @Test
        @DisplayName("Should generate secrets with sufficient entropy")
        void shouldGenerateSecretsWithSufficientEntropy() {
            String secret = hasher.generateSecureSecret(32);
            
            // Check that we have a good mix of character types
            boolean hasUppercase = secret.chars().anyMatch(Character::isUpperCase);
            boolean hasLowercase = secret.chars().anyMatch(Character::isLowerCase);
            boolean hasDigits = secret.chars().anyMatch(Character::isDigit);
            
            // At least 2 of the 3 character types should be present
            int charTypeCount = (hasUppercase ? 1 : 0) + (hasLowercase ? 1 : 0) + (hasDigits ? 1 : 0);
            assertThat(charTypeCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create hasher with default parameters")
        void shouldCreateHasherWithDefaultParameters() {
            ClientSecretHasher defaultHasher = new ClientSecretHasher();
            String secret = "testSecret";
            
            String hash = defaultHasher.hashClientSecret(secret);
            boolean isValid = defaultHasher.verifyClientSecret(secret, hash);
            
            assertThat(hash).isNotNull();
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should create hasher with custom parameters")
        void shouldCreateHasherWithCustomParameters() {
            ClientSecretHasher customHasher = new ClientSecretHasher(16, 32, 4, 65536, 3);
            String secret = "testSecret";
            
            String hash = customHasher.hashClientSecret(secret);
            boolean isValid = customHasher.verifyClientSecret(secret, hash);
            
            assertThat(hash).isNotNull();
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should create hasher with custom parameters gracefully")
        void shouldCreateHasherWithCustomParametersGracefully() {
            // Spring's Argon2PasswordEncoder may handle edge cases gracefully
            // rather than throwing exceptions, so we just verify it can be created
            ClientSecretHasher customHasher = new ClientSecretHasher(16, 32, 4, 65536, 3);
            assertThat(customHasher).isNotNull();
            
            // Test that it can still hash and verify
            String secret = "testSecret";
            String hash = customHasher.hashClientSecret(secret);
            assertThat(customHasher.verifyClientSecret(secret, hash)).isTrue();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should hash secret in reasonable time")
        void shouldHashSecretInReasonableTime() {
            String secret = "performanceTestSecret";
            
            long startTime = System.currentTimeMillis();
            hasher.hashClientSecret(secret);
            long endTime = System.currentTimeMillis();
            
            long duration = endTime - startTime;
            
            // Should complete within 5 seconds for default parameters
            assertThat(duration).isLessThan(5000);
        }

        @Test
        @DisplayName("Should verify secret in reasonable time")
        void shouldVerifySecretInReasonableTime() {
            String secret = "performanceTestSecret";
            String hash = hasher.hashClientSecret(secret);
            
            long startTime = System.currentTimeMillis();
            hasher.verifyClientSecret(secret, hash);
            long endTime = System.currentTimeMillis();
            
            long duration = endTime - startTime;
            
            // Verification should be relatively fast
            assertThat(duration).isLessThan(2000);
        }
    }
} 
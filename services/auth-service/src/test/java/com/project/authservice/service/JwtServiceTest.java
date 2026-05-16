package com.project.authservice.service;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.security.RsaKeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceTest {

    @Mock private RsaKeyProperties rsaKeys;

    private JwtService jwtService;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey = (RSAPublicKey) pair.getPublic();

        when(rsaKeys.getPrivateKey()).thenReturn(privateKey);
        when(rsaKeys.getPublicKey()).thenReturn(publicKey);

        jwtService = new JwtService(rsaKeys);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);

        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("ROLE_CUSTOMER");
        Permission perm = new Permission();
        perm.setId(UUID.randomUUID());
        perm.setName("products:read");
        role.setPermissions(Set.of(perm));

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setRoles(Set.of(role));
    }

    @Test
    void generateToken_ContainsUserClaims() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();

        String subject = jwtService.getUserIdFromToken(token);
        assertThat(subject).isEqualTo(user.getId().toString());

        String email = jwtService.getEmailFromToken(token);
        assertThat(email).isEqualTo("test@example.com");

        Set<String> roles = jwtService.getRolesFromToken(token);
        assertThat(roles).contains("ROLE_CUSTOMER");

        Set<String> permissions = jwtService.getPermissionsFromToken(token);
        assertThat(permissions).contains("products:read");
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtService.generateToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_InvalidToken_ReturnsFalse() {
        assertThat(jwtService.validateToken("invalid-token")).isFalse();
    }
}

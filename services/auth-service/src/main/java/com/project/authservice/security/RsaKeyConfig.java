package com.project.authservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class RsaKeyConfig {

    private final RsaKeyProperties rsaKeyProperties;

    public RsaKeyConfig(RsaKeyProperties rsaKeyProperties) {
        this.rsaKeyProperties = rsaKeyProperties;
    }

    @Bean
    @Primary
    public RsaKeyProperties activeRsaKeyProperties() {
        if (rsaKeyProperties.getPublicKey() == null || rsaKeyProperties.getPrivateKey() == null) {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                
                RsaKeyProperties generatedProps = new RsaKeyProperties();
                generatedProps.setPublicKey((RSAPublicKey) keyPair.getPublic());
                generatedProps.setPrivateKey((RSAPrivateKey) keyPair.getPrivate());
                return generatedProps;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate RSA keys", e);
            }
        }
        return rsaKeyProperties;
    }
}

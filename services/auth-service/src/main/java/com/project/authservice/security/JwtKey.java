package com.project.authservice.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single signing key with its identifier. Multiple instances allow rotation: the {@link
 * KeyManager} keeps a "current" key (used to sign new JWTs) and optionally a "previous" key (still
 * used to verify outstanding tokens).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtKey {
  /** {@code kid} included in the JWT header and JWKS document. */
  private String kid;

  private RSAPublicKey publicKey;
  private RSAPrivateKey privateKey;
}

package com.project.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class HmacSignatureVerifierTest {
  private static final String SECRET = "test-webhook-secret";
  private static final String PAYLOAD = "{\"paymentId\":\"payment-1\"}";

  @Test
  void computesKnownHmacSha256Vector() {
    assertThat(HmacSignatureVerifier.sign("key", "The quick brown fox jumps over the lazy dog"))
        .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    assertThatThrownBy(() -> HmacSignatureVerifier.sign("", PAYLOAD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to compute HMAC");
  }

  @Test
  void acceptsAuthenticPayloadButRejectsChangedBytesAndSecrets() {
    String header = HmacSignatureVerifier.header(SECRET, PAYLOAD);
    assertThat(HmacSignatureVerifier.verify(SECRET, header, PAYLOAD, 60)).isTrue();
    assertThat(HmacSignatureVerifier.verify(SECRET, header, PAYLOAD + " ", 60)).isFalse();
    assertThat(HmacSignatureVerifier.verify("wrong-secret", header, PAYLOAD, 60)).isFalse();
    assertThat(HmacSignatureVerifier.verify(null, header, PAYLOAD, 60)).isFalse();
    assertThat(HmacSignatureVerifier.verify(SECRET, null, PAYLOAD, 60)).isFalse();
    assertThat(HmacSignatureVerifier.verify(SECRET, header, null, 60)).isFalse();
    assertThat(HmacSignatureVerifier.verify(SECRET, "ignored,other=value," + header, PAYLOAD, 60))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "t=1", "v1=signature", "t=invalid,v1=signature", "t=1,v1=signature"})
  void rejectsIncompleteMalformedAndExpiredHeaders(String header) {
    assertThat(HmacSignatureVerifier.verify(SECRET, header, PAYLOAD, 60)).isFalse();
  }

  @Test
  void prefersInternalSignatureHeaderAndFallsBackToStripe() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    assertThat(HmacSignatureVerifier.extractSignatureHeader(request)).isNull();
    request.addHeader("Stripe-Signature", "stripe-signature");
    assertThat(HmacSignatureVerifier.extractSignatureHeader(request)).isEqualTo("stripe-signature");
    request.addHeader("X-Webhook-Signature", "internal-signature");
    assertThat(HmacSignatureVerifier.extractSignatureHeader(request))
        .isEqualTo("internal-signature");
  }
}

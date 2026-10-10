package com.project.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ResourceServerSecurityConfigTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ResourceServerSecurityConfig.class,
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class))
          .withUserConfiguration(WebSecurity.class)
          .withPropertyValues(
              "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused");

  @Test
  void sharedDefaultStillChallengesAnonymousRequests() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(SecurityFilterChain.class);
          assertThat(context).hasBean("commonSecurityFilterChain");
          var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
          mvc.perform(get("/private")).andExpect(status().isUnauthorized());
        });
  }

  @Test
  void serviceChainOwnsAuthorizationWithoutADuplicateSharedChain() {
    runner
        .withUserConfiguration(ServiceSecurity.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecurityFilterChain.class);
              assertThat(context).doesNotHaveBean("commonSecurityFilterChain");
              var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
              mvc.perform(get("/private")).andExpect(status().isForbidden());
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class WebSecurity {
    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ServiceSecurity {
    @Bean
    SecurityFilterChain serviceChain(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
    }
  }

  @RestController
  static class Probe {
    @GetMapping("/private")
    String privateEndpoint() {
      return "private";
    }
  }
}

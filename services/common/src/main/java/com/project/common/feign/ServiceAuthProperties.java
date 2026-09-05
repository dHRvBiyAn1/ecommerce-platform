package com.project.common.feign;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "service.auth")
public class ServiceAuthProperties {
    private String tokenUri;
    private String clientId;
    private String clientSecret;
    private String scope;
}

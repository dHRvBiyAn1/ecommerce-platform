package com.project.common.feign;

public interface ServiceTokenClient {
    ServiceTokenResponse requestToken(ServiceAuthProperties properties);
}

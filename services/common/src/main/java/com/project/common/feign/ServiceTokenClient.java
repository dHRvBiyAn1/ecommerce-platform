package com.project.common.feign;

import com.project.common.generated.token.ServiceTokenResponse;

public interface ServiceTokenClient {
    ServiceTokenResponse requestToken(ServiceAuthProperties properties);
}

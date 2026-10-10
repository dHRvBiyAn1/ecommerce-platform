package com.project.order.application.mapper;

import com.project.order.generated.model.ApiResponsePageOrderResponseData;
import com.project.order.generated.model.OrderResponse;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", implementationPackage = "com.project.order.generated.mapper")
public interface OrderApiMapper {
  ApiResponsePageOrderResponseData toApi(Page<OrderResponse> page);
}

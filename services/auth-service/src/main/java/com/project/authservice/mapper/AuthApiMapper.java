package com.project.authservice.mapper;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.generated.model.ApiResponseListRole;
import com.project.authservice.generated.model.ApiResponsePageSellerApplicationResponse;
import com.project.authservice.generated.model.ApiResponsePageUserProfileDto;
import com.project.authservice.generated.model.ApiResponseRole;
import com.project.authservice.generated.model.ApiResponseSellerApplicationResponse;
import com.project.authservice.generated.model.ApiResponseUserProfileDto;
import com.project.authservice.generated.model.ApiResponseVoid;
import com.project.authservice.generated.model.PageSellerApplicationResponse;
import com.project.authservice.generated.model.PageUserProfileDto;
import com.project.authservice.generated.model.PageableObject;
import com.project.authservice.generated.model.SellerApplicationResponse;
import com.project.authservice.generated.model.SortObject;
import com.project.authservice.generated.model.UserProfileDto;
import com.project.common.generated.model.ResponseEnvelope;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class AuthApiMapper {

  public ApiResponseUserProfileDto toApiUserProfile(ResponseEnvelope<UserProfileDto> response) {
    return new ApiResponseUserProfileDto()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(response.getData())
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponseSellerApplicationResponse toApiSellerApplication(
      ResponseEnvelope<SellerApplicationResponse> response) {
    return new ApiResponseSellerApplicationResponse()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(response.getData())
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponseRole toApiRole(
      ResponseEnvelope<com.project.authservice.generated.model.Role> response) {
    return new ApiResponseRole()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(response.getData())
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponseListRole toApiRoles(
      ResponseEnvelope<java.util.List<com.project.authservice.generated.model.Role>> response) {
    return new ApiResponseListRole()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(response.getData())
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponseVoid toApiVoid(ResponseEnvelope<Void> response) {
    return new ApiResponseVoid()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(null)
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponsePageUserProfileDto toApiUserProfiles(
      ResponseEnvelope<Page<UserProfileDto>> response) {
    return new ApiResponsePageUserProfileDto()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(toApiUserProfiles(response.getData()))
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public ApiResponsePageSellerApplicationResponse toApiSellerApplications(
      ResponseEnvelope<Page<SellerApplicationResponse>> response) {
    return new ApiResponsePageSellerApplicationResponse()
        .status(response.getStatus())
        .message(response.getMessage())
        .data(toApiSellerApplications(response.getData()))
        .traceId(response.getTraceId())
        .timestamp(timestamp(response));
  }

  public PageUserProfileDto toApiUserProfiles(Page<UserProfileDto> page) {
    SortObject sort = toApi(page.getSort());
    var pageable = page.getPageable();
    PageableObject pageableObject =
        new PageableObject()
            .offset(pageable.getOffset())
            .sort(sort)
            .paged(pageable.isPaged())
            .pageNumber(pageable.getPageNumber())
            .pageSize(pageable.getPageSize())
            .unpaged(pageable.isUnpaged());
    return new PageUserProfileDto()
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .size(page.getSize())
        .content(page.getContent())
        .number(page.getNumber())
        .sort(sort)
        .pageable(pageableObject)
        .numberOfElements(page.getNumberOfElements())
        .first(page.isFirst())
        .last(page.isLast())
        .empty(page.isEmpty());
  }

  public PageSellerApplicationResponse toApiSellerApplications(
      Page<SellerApplicationResponse> page) {
    SortObject sort = toApi(page.getSort());
    var pageable = page.getPageable();
    PageableObject pageableObject =
        new PageableObject()
            .offset(pageable.getOffset())
            .sort(sort)
            .paged(pageable.isPaged())
            .pageNumber(pageable.getPageNumber())
            .pageSize(pageable.getPageSize())
            .unpaged(pageable.isUnpaged());
    return new PageSellerApplicationResponse()
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .size(page.getSize())
        .content(page.getContent())
        .number(page.getNumber())
        .sort(sort)
        .pageable(pageableObject)
        .numberOfElements(page.getNumberOfElements())
        .first(page.isFirst())
        .last(page.isLast())
        .empty(page.isEmpty());
  }

  public com.project.authservice.generated.model.Role toApi(Role role) {
    return new com.project.authservice.generated.model.Role()
        .id(role.getId())
        .name(role.getName())
        .permissions(Set.copyOf(role.getPermissions().stream().map(this::toApi).toList()));
  }

  public com.project.authservice.generated.model.Permission toApi(Permission permission) {
    return new com.project.authservice.generated.model.Permission()
        .id(permission.getId())
        .name(permission.getName());
  }

  private static SortObject toApi(Sort sort) {
    return new SortObject()
        .empty(sort.isEmpty())
        .sorted(sort.isSorted())
        .unsorted(sort.isUnsorted());
  }

  private static OffsetDateTime timestamp(ResponseEnvelope<?> response) {
    return response.getTimestamp() == null
        ? null
        : OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC);
  }
}

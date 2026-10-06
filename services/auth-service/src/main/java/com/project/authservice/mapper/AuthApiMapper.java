package com.project.authservice.mapper;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.dto.response.seller.SellerApplicationResponse;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.common.dto.ApiResponse;
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
import com.project.authservice.generated.model.SortObject;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@Mapper(componentModel = "spring", implementationPackage = "com.project.authservice.generated.mapper")
public interface AuthApiMapper {

    com.project.authservice.dto.request.RegistrationRequest toDomain(
            com.project.authservice.generated.model.RegistrationRequest request);

    com.project.authservice.dto.request.ChangePasswordRequest toDomain(
            com.project.authservice.generated.model.ChangePasswordRequest request);

    com.project.authservice.dto.request.UserUpdateRequest toDomain(
            com.project.authservice.generated.model.UserUpdateRequest request);

    com.project.authservice.dto.request.seller.SellerApplicationRequest toDomain(
            com.project.authservice.generated.model.SellerApplicationRequest request);

    com.project.authservice.dto.request.seller.RejectApplicationRequest toDomain(
            com.project.authservice.generated.model.RejectApplicationRequest request);

    com.project.authservice.dto.request.admin.CreateRoleRequest toDomain(
            com.project.authservice.generated.model.CreateRoleRequest request);

    com.project.authservice.dto.request.admin.AssignRolesRequest toDomain(
            com.project.authservice.generated.model.AssignRolesRequest request);

    com.project.authservice.dto.request.admin.UpdateRolePermissionsRequest toDomain(
            com.project.authservice.generated.model.UpdateRolePermissionsRequest request);

    AddressDto toDomain(com.project.authservice.generated.model.AddressDto address);

    com.project.authservice.generated.model.AddressDto toApi(AddressDto address);

    com.project.authservice.generated.model.UserProfileDto toApi(UserProfileDto profile);

    com.project.authservice.generated.model.SellerApplicationResponse toApi(
            SellerApplicationResponse application);

    com.project.authservice.generated.model.Role toApi(Role role);

    com.project.authservice.generated.model.Permission toApi(Permission permission);

    default ApiResponseUserProfileDto toApiUserProfile(ApiResponse<UserProfileDto> response) {
        return new ApiResponseUserProfileDto().status(response.getStatus()).message(response.getMessage())
                .data(response.getData() == null ? null : toApi(response.getData()))
                .traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponseSellerApplicationResponse toApiSellerApplication(
            ApiResponse<SellerApplicationResponse> response) {
        return new ApiResponseSellerApplicationResponse().status(response.getStatus()).message(response.getMessage())
                .data(response.getData() == null ? null : toApi(response.getData()))
                .traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponseRole toApiRole(ApiResponse<Role> response) {
        return new ApiResponseRole().status(response.getStatus()).message(response.getMessage())
                .data(response.getData() == null ? null : toApi(response.getData()))
                .traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponseListRole toApiRoles(ApiResponse<java.util.List<Role>> response) {
        return new ApiResponseListRole().status(response.getStatus()).message(response.getMessage())
                .data(response.getData().stream().map(this::toApi).toList())
                .traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponseVoid toApiVoid(ApiResponse<Void> response) {
        return new ApiResponseVoid().status(response.getStatus()).message(response.getMessage()).data(null)
                .traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponsePageUserProfileDto toApiUserProfiles(ApiResponse<Page<UserProfileDto>> response) {
        return new ApiResponsePageUserProfileDto().status(response.getStatus()).message(response.getMessage())
                .data(toApiUserProfiles(response.getData())).traceId(response.getTraceId()).timestamp(timestamp(response));
    }

    default ApiResponsePageSellerApplicationResponse toApiSellerApplications(
            ApiResponse<Page<SellerApplicationResponse>> response) {
        return new ApiResponsePageSellerApplicationResponse().status(response.getStatus()).message(response.getMessage())
                .data(toApiSellerApplications(response.getData())).traceId(response.getTraceId())
                .timestamp(timestamp(response));
    }

    default PageUserProfileDto toApiUserProfiles(Page<UserProfileDto> page) {
        var pageable = page.getPageable();
        SortObject sort = toApi(page.getSort());
        PageableObject pageableObject = new PageableObject().offset(pageable.getOffset()).sort(sort)
                .paged(pageable.isPaged()).pageNumber(pageable.getPageNumber()).pageSize(pageable.getPageSize())
                .unpaged(pageable.isUnpaged());
        return new PageUserProfileDto().totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .size(page.getSize()).content(page.getContent().stream().map(this::toApi).toList())
                .number(page.getNumber()).sort(sort).pageable(pageableObject)
                .numberOfElements(page.getNumberOfElements()).first(page.isFirst()).last(page.isLast())
                .empty(page.isEmpty());
    }

    default PageSellerApplicationResponse toApiSellerApplications(Page<SellerApplicationResponse> page) {
        var pageable = page.getPageable();
        SortObject sort = toApi(page.getSort());
        PageableObject pageableObject = new PageableObject().offset(pageable.getOffset()).sort(sort)
                .paged(pageable.isPaged()).pageNumber(pageable.getPageNumber()).pageSize(pageable.getPageSize())
                .unpaged(pageable.isUnpaged());
        return new PageSellerApplicationResponse().totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .size(page.getSize()).content(page.getContent().stream().map(this::toApi).toList())
                .number(page.getNumber()).sort(sort).pageable(pageableObject)
                .numberOfElements(page.getNumberOfElements()).first(page.isFirst()).last(page.isLast())
                .empty(page.isEmpty());
    }

    private static SortObject toApi(Sort sort) {
        return new SortObject().empty(sort.isEmpty()).sorted(sort.isSorted()).unsorted(sort.isUnsorted());
    }

    private static java.time.OffsetDateTime timestamp(ApiResponse<?> response) {
        return response.getTimestamp() == null ? null
                : java.time.OffsetDateTime.ofInstant(response.getTimestamp(), java.time.ZoneOffset.UTC);
    }
}

package com.project.authservice.controller;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.entity.User;
import com.project.authservice.generated.api.AdministrationApi;
import com.project.authservice.generated.model.ApiResponseListRole;
import com.project.authservice.generated.model.ApiResponsePageSellerApplicationResponse;
import com.project.authservice.generated.model.ApiResponsePageUserProfileDto;
import com.project.authservice.generated.model.ApiResponseRole;
import com.project.authservice.generated.model.ApiResponseSellerApplicationResponse;
import com.project.authservice.generated.model.AssignRolesRequest;
import com.project.authservice.generated.model.CreateRoleRequest;
import com.project.authservice.generated.model.RejectApplicationRequest;
import com.project.authservice.generated.model.UpdateRolePermissionsRequest;
import com.project.authservice.mapper.AuthApiMapper;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.SellerApplicationService;
import com.project.authservice.service.UserProfileService;
import com.project.common.constant.Permissions;
import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.web.Responses;
import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminController implements AdministrationApi {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final UserProfileService userProfileService;
  private final SellerApplicationService sellerApplicationService;
  private final AuthenticatedUserValidator authenticatedUserValidator;
  private final AuthApiMapper apiMapper;

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.USERS_WRITE + "')")
  public ResponseEntity<ApiResponseSellerApplicationResponse> approve(UUID id) {
    UUID adminId =
        authenticatedUserValidator.requireUserId(
            SecurityContextHolder.getContext().getAuthentication());
    return ResponseEntity.ok(
        apiMapper.toApiSellerApplication(
            Responses.success(sellerApplicationService.approve(id, adminId))));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.USERS_READ + "')")
  public ResponseEntity<ApiResponsePageSellerApplicationResponse> callList(
      com.project.authservice.generated.model.SellerApplicationStatus status,
      @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    SellerApplicationStatus filter =
        status == null ? null : SellerApplicationStatus.valueOf(status.getValue());
    return ResponseEntity.ok(
        apiMapper.toApiSellerApplications(
            Responses.success(sellerApplicationService.list(filter, pageable))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:roles:write')")
  @Transactional
  public ResponseEntity<ApiResponseRole> createRole(CreateRoleRequest createRoleRequest) {
    if (roleRepository.findByName(createRoleRequest.getName()).isPresent()) {
      throw new DuplicateResourceException("Role already exists: " + createRoleRequest.getName());
    }
    Role role = new Role();
    role.setName(createRoleRequest.getName());
    role = roleRepository.save(role);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(apiMapper.toApiRole(Responses.created(apiMapper.toApi(role))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:roles:write')")
  @Transactional
  public ResponseEntity<Void> deleteRole(UUID roleId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    roleRepository.delete(role);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.USERS_READ + "')")
  public ResponseEntity<ApiResponseSellerApplicationResponse> get(UUID id) {
    return ResponseEntity.ok(
        apiMapper.toApiSellerApplication(Responses.success(sellerApplicationService.get(id))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:users:read')")
  public ResponseEntity<com.project.authservice.generated.model.ApiResponseUserProfileDto> getUser(
      UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    return ResponseEntity.ok(
        apiMapper.toApiUserProfile(Responses.success(userProfileService.toDto(user))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:roles:read')")
  public ResponseEntity<ApiResponseListRole> listRoles() {
    return ResponseEntity.ok(
        apiMapper.toApiRoles(
            Responses.success(roleRepository.findAll().stream().map(apiMapper::toApi).toList())));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:users:read')")
  public ResponseEntity<ApiResponsePageUserProfileDto> listUsers(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        apiMapper.toApiUserProfiles(
            Responses.success(userRepository.findAll(pageable).map(userProfileService::toDto))));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.USERS_WRITE + "')")
  public ResponseEntity<ApiResponseSellerApplicationResponse> reject(
      UUID id, RejectApplicationRequest rejectRequest) {
    UUID adminId =
        authenticatedUserValidator.requireUserId(
            SecurityContextHolder.getContext().getAuthentication());
    return ResponseEntity.ok(
        apiMapper.toApiSellerApplication(
            Responses.success(sellerApplicationService.reject(id, adminId, rejectRequest))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:users:write')")
  @Transactional
  public ResponseEntity<com.project.authservice.generated.model.ApiResponseUserProfileDto>
      setActive(UUID userId, Boolean active) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    user.setActive(active);
    return ResponseEntity.ok(
        apiMapper.toApiUserProfile(
            Responses.success(userProfileService.toDto(userRepository.save(user)))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:users:write')")
  @Transactional
  public ResponseEntity<com.project.authservice.generated.model.ApiResponseUserProfileDto> setRoles(
      UUID userId, AssignRolesRequest assignRolesRequest) {

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    Set<Role> roles =
        assignRolesRequest.getRoles().stream()
            .map(
                name ->
                    roleRepository
                        .findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", name)))
            .collect(Collectors.toSet());
    user.setRoles(roles);
    return ResponseEntity.ok(
        apiMapper.toApiUserProfile(
            Responses.success(userProfileService.toDto(userRepository.save(user)))));
  }

  @Override
  @PreAuthorize("hasAuthority('admin:roles:write')")
  @Transactional
  public ResponseEntity<ApiResponseRole> updateRolePermissions(
      UUID roleId, UpdateRolePermissionsRequest permissionsRequest) {

    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    Set<Permission> permissions =
        permissionsRequest.getPermissions().stream()
            .map(
                name ->
                    permissionRepository
                        .findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", name)))
            .collect(Collectors.toSet());
    role.setPermissions(permissions);
    return ResponseEntity.ok(
        apiMapper.toApiRole(Responses.success(apiMapper.toApi(roleRepository.save(role)))));
  }
}

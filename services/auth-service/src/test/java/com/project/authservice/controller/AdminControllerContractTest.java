package com.project.authservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.AssignRolesRequest;
import com.project.authservice.generated.model.CreateRoleRequest;
import com.project.authservice.generated.model.UpdateRolePermissionsRequest;
import com.project.authservice.generated.model.UserProfileDto;
import com.project.authservice.mapper.AuthApiMapper;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.SellerApplicationService;
import com.project.authservice.service.UserProfileService;
import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class AdminControllerContractTest {

  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final PermissionRepository permissions = mock(PermissionRepository.class);
  private final UserProfileService profiles = mock(UserProfileService.class);
  private AdminController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminController(
            users,
            roles,
            permissions,
            profiles,
            mock(SellerApplicationService.class),
            new AuthenticatedUserValidator(),
            new AuthApiMapper());
  }

  @Test
  void listsUsersAsGeneratedProfilesWithinTheRequestedPage() {
    UUID id = UUID.randomUUID();
    User user = new User();
    user.setId(id);
    UserProfileDto profile = new UserProfileDto().id(id).email("user@example.com");
    when(users.findAll(PageRequest.of(0, 5)))
        .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 5), 1));
    when(profiles.toDto(user)).thenReturn(profile);

    var response = controller.listUsers(PageRequest.of(0, 5));

    assertThat(response.getBody().getData().getContent()).containsExactly(profile);
    assertThat(response.getBody().getData().getTotalElements()).isEqualTo(1);
  }

  @Test
  void returnsUserProfileAndReportsMissingUser() {
    UUID id = UUID.randomUUID();
    User user = new User();
    user.setId(id);
    when(users.findById(id)).thenReturn(Optional.of(user));
    when(profiles.toDto(user)).thenReturn(new UserProfileDto().id(id));

    assertThat(controller.getUser(id).getBody().getData().getId()).isEqualTo(id);

    when(users.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> controller.getUser(id)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listsRolesAndCreatesRoleWithCreatedResponse() {
    Role existing = role("ROLE_CUSTOMER");
    when(roles.findAll()).thenReturn(List.of(existing));
    assertThat(controller.listRoles().getBody().getData())
        .extracting("name")
        .containsExactly("ROLE_CUSTOMER");

    when(roles.findByName("ROLE_SUPPORT")).thenReturn(Optional.empty());
    when(roles.save(org.mockito.ArgumentMatchers.any(Role.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = controller.createRole(new CreateRoleRequest().name("ROLE_SUPPORT"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getData().getName()).isEqualTo("ROLE_SUPPORT");
  }

  @Test
  void rejectsDuplicateRoleAndResolvesPermissionsBeforeSavingUpdates() {
    Role role = role("ROLE_SUPPORT");
    when(roles.findByName("ROLE_SUPPORT")).thenReturn(Optional.of(role));
    assertThatThrownBy(() -> controller.createRole(new CreateRoleRequest().name("ROLE_SUPPORT")))
        .isInstanceOf(DuplicateResourceException.class);

    UUID roleId = UUID.randomUUID();
    Permission permission = permission("users:read");
    when(roles.findById(roleId)).thenReturn(Optional.of(role));
    when(permissions.findByName("users:read")).thenReturn(Optional.of(permission));
    when(roles.save(role)).thenReturn(role);

    var response =
        controller.updateRolePermissions(
            roleId, new UpdateRolePermissionsRequest().permissions(List.of("users:read")));

    assertThat(response.getBody().getData().getPermissions())
        .extracting("name")
        .containsExactly("users:read");

    when(permissions.findByName("missing:permission")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                controller.updateRolePermissions(
                    roleId,
                    new UpdateRolePermissionsRequest().permissions(List.of("missing:permission"))))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void assignsRolesAndChangesUserActiveState() {
    UUID id = UUID.randomUUID();
    User user = new User();
    user.setId(id);
    Role role = role("ROLE_SELLER");
    when(users.findById(id)).thenReturn(Optional.of(user));
    when(roles.findByName("ROLE_SELLER")).thenReturn(Optional.of(role));
    when(users.save(user)).thenReturn(user);
    when(profiles.toDto(user)).thenReturn(new UserProfileDto().id(id));

    var assignment =
        controller.setRoles(id, new AssignRolesRequest().roles(List.of("ROLE_SELLER")));
    assertThat(assignment.getBody().getData().getId()).isEqualTo(id);
    assertThat(user.getRoles()).containsExactly(role);

    var active = controller.setActive(id, false);
    assertThat(user.isActive()).isFalse();
    assertThat(active.getBody().getData().getId()).isEqualTo(id);

    when(users.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> controller.setRoles(id, new AssignRolesRequest().roles(List.of("ROLE_SELLER"))))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void reportsMissingRoleDuringAssignmentAndDeletesExistingRole() {
    UUID id = UUID.randomUUID();
    when(users.findById(id)).thenReturn(Optional.of(new User()));
    when(roles.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> controller.setRoles(id, new AssignRolesRequest().roles(List.of("ROLE_UNKNOWN"))))
        .isInstanceOf(ResourceNotFoundException.class);

    UUID roleId = UUID.randomUUID();
    Role role = role("ROLE_RETIRED");
    when(roles.findById(roleId)).thenReturn(Optional.of(role));
    assertThat(controller.deleteRole(roleId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(roles).delete(role);

    when(roles.findById(roleId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> controller.deleteRole(roleId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private Role role(String name) {
    Role role = new Role();
    role.setName(name);
    return role;
  }

  private Permission permission(String name) {
    Permission permission = new Permission();
    permission.setName(name);
    return permission;
  }
}

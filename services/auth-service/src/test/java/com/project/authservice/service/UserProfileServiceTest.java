package com.project.authservice.service;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.dto.request.UserUpdateRequest;
import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.AddressMapper;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserCredentialRepository credentialRepository;
    @Mock UserMapper userMapper;
    @Mock AddressMapper addressMapper;
    @InjectMocks UserProfileService service;

    @Test
    void updateChangesOnlyNonNullFieldsAndMapsResolvedPasswordState() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setDisplayName("Before");
        user.setImageUrl("before.png");
        user.setPhone("+1 555 0100");
        user.setShippingAddress(com.project.authservice.entity.Address.builder().street("Shipping").build());
        user.setBillingAddress(com.project.authservice.entity.Address.builder().street("Billing").build());
        AddressDto shipping = new AddressDto("Customer One", "+1 555 0100", "1 Main Street", "Pune",
                "MH", "411001", "IN");
        UserProfileDto profile = new UserProfileDto(userId, null, "Before", "before.png", "+1 555 0100", true, null,
                java.util.Set.of(), java.util.Set.of(), shipping, null, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(credentialRepository.findByUserIdAndAuthProvider(userId, AuthProvider.LOCAL))
                .thenReturn(Optional.of(new com.project.authservice.entity.UserCredential()));
        when(userMapper.toDto(user, true)).thenReturn(profile);
        UserProfileDto result = service.updateProfile(userId,
                new UserUpdateRequest(null, null, null, null, null));

        assertThat(user.getDisplayName()).isEqualTo("Before");
        assertThat(user.getImageUrl()).isEqualTo("before.png");
        assertThat(user.getPhone()).isEqualTo("+1 555 0100");
        assertThat(user.getShippingAddress().getStreet()).isEqualTo("Shipping");
        assertThat(user.getBillingAddress().getStreet()).isEqualTo("Billing");
        assertThat(result).isSameAs(profile);
        verify(userRepository).save(user);
        verify(userMapper).toDto(user, true);
    }

    @Test
    void hasPasswordUsesOnlyLocalCredential() {
        UUID userId = UUID.randomUUID();
        when(credentialRepository.findByUserIdAndAuthProvider(userId, AuthProvider.LOCAL))
                .thenReturn(Optional.empty());

        assertThat(service.hasPassword(userId)).isFalse();
    }
}

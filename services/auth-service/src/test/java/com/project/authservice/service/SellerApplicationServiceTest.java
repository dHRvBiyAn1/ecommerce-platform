package com.project.authservice.service;

import com.project.authservice.dto.AddressDto;
import com.project.authservice.dto.request.seller.SellerApplicationRequest;
import com.project.authservice.dto.response.seller.SellerApplicationResponse;
import com.project.authservice.entity.Address;
import com.project.authservice.entity.SellerApplication;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.AddressMapper;
import com.project.authservice.mapper.SellerApplicationMapper;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.SellerApplicationRepository;
import com.project.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    @Mock SellerApplicationRepository repository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock AddressMapper addressMapper;
    @Mock SellerApplicationMapper sellerApplicationMapper;
    @InjectMocks SellerApplicationService service;

    @Test
    void applyMapsPickupAddressAndDelegatesResponseWithResolvedUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AddressDto pickupAddress = new AddressDto("Asha", "+91 9876543210", "1 Main St", "Pune", "MH", "411001", "IN");
        SellerApplicationRequest request = new SellerApplicationRequest("Acme", "", "+91 9876543210", pickupAddress,
                "", "Call first");
        Address mappedAddress = Address.builder().street("1 Main St").country("IN").build();
        User user = new User();
        user.setEmail("seller@example.com");
        SellerApplicationResponse expected = new SellerApplicationResponse(null, userId, "seller@example.com", null,
                SellerApplicationStatus.PENDING, "Acme", null, "+91 9876543210", pickupAddress, null,
                "Call first", null, null, null, null);
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(addressMapper.toEntity(pickupAddress)).thenReturn(mappedAddress);
        when(repository.save(any(SellerApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sellerApplicationMapper.toResponse(any(SellerApplication.class), same(user))).thenReturn(expected);

        SellerApplicationResponse result = service.apply(userId, request);

        ArgumentCaptor<SellerApplication> application = ArgumentCaptor.forClass(SellerApplication.class);
        verify(addressMapper).toEntity(pickupAddress);
        verify(repository).save(application.capture());
        verify(sellerApplicationMapper).toResponse(same(application.getValue()), same(user));
        assertThat(application.getValue().getPickupAddress()).isSameAs(mappedAddress);
        assertThat(application.getValue().getGstin()).isNull();
        assertThat(application.getValue().getBankAccountLast4()).isNull();
        assertThat(result).isSameAs(expected);
    }
}

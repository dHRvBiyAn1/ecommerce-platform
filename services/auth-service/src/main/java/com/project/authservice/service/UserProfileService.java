package com.project.authservice.service;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.User;
import com.project.authservice.generated.model.UserProfileDto;
import com.project.authservice.generated.model.UserUpdateRequest;
import com.project.authservice.mapper.AddressMapper;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import com.project.common.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileService {

  private final UserRepository userRepository;
  private final UserCredentialRepository userCredentialRepository;
  private final UserMapper userMapper;
  private final AddressMapper addressMapper;

  @Transactional
  public UserProfileDto getProfile(UUID userId) {
    return toDto(findUser(userId));
  }

  @Transactional
  public UserProfileDto updateProfile(UUID userId, UserUpdateRequest request) {
    User user = findUser(userId);
    if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
    if (request.getImageUrl() != null) user.setImageUrl(request.getImageUrl());
    if (request.getPhone() != null) user.setPhone(request.getPhone());
    if (request.getShippingAddress() != null)
      user.setShippingAddress(addressMapper.toEntity(request.getShippingAddress()));
    if (request.getBillingAddress() != null)
      user.setBillingAddress(addressMapper.toEntity(request.getBillingAddress()));
    return toDto(userRepository.save(user));
  }

  @Transactional
  public UserProfileDto toDto(User user) {
    return userMapper.toDto(user, hasPassword(user.getId()));
  }

  @Transactional
  public boolean hasPassword(UUID userId) {
    return userCredentialRepository
        .findByUserIdAndAuthProvider(userId, AuthProvider.LOCAL)
        .isPresent();
  }

  private User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }
}

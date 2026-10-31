package com.findit.service;

import com.findit.domain.User;
import com.findit.dto.auth.UserResponse;
import com.findit.dto.profile.UpdateProfileRequest;
import com.findit.exception.NotFoundException;
import com.findit.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read and update access to user accounts.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.isActive(), user.getContactChannel(), user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return toResponse(getById(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        user.setDisplayName(request.displayName().trim());
        user.setContactChannel(request.contactChannel() == null || request.contactChannel().isBlank()
                ? null
                : request.contactChannel().trim());
        return toResponse(userRepository.save(user));
    }
}

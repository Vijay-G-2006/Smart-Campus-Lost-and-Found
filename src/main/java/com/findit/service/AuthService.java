package com.findit.service;

import com.findit.domain.Role;
import com.findit.domain.User;
import com.findit.dto.auth.RegisterRequest;
import com.findit.dto.auth.UserResponse;
import com.findit.exception.ConflictException;
import com.findit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration. New accounts always get the {@code USER} role - the role is
 * never taken from the request body.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserService userService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Do not reveal whether the address is already registered.
            throw new ConflictException("That email address cannot be registered");
        }

        User user = new User(email, request.displayName().trim(), passwordEncoder.encode(request.password()), Role.USER);
        user.setActive(true);
        User saved = userRepository.save(user);
        log.info("Registered new user {}", saved.getId());
        auditService.record(saved.getId(), "USER_REGISTERED", "USER", saved.getId(), null);
        return userService.toResponse(saved);
    }
}

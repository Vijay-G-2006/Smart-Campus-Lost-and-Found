package com.findit.controller;

import com.findit.dto.auth.UserResponse;
import com.findit.dto.profile.UpdateProfileRequest;
import com.findit.security.AppUserPrincipal;
import com.findit.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile of the signed in user.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.getProfile(principal.getId());
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AppUserPrincipal principal,
                                @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.getId(), request);
    }
}

package com.primevest.domain.user;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.user.dto.UserResponseDTO;
import com.primevest.domain.user.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        return ResponseEntity.ok(userService.getUserById(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(@Valid @RequestBody UserUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateUser(SecurityUtils.getCurrentUserId(), dto));
    }
}

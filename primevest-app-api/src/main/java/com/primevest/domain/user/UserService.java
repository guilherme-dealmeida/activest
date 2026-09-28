package com.primevest.domain.user;

import com.primevest.domain.user.dto.UserResponseDTO;
import com.primevest.domain.user.dto.UserUpdateDTO;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Gets user by ID.
     */
    public UserResponseDTO getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDTO(user);
    }

    /**
     * Updates a user.
     */
    @Transactional
    public UserResponseDTO updateUser(UUID userId, UserUpdateDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setName(dto.name());
        user.setAvatarUrl(dto.avatarUrl());
        
        return toDTO(userRepository.save(user));
    }

    private UserResponseDTO toDTO(User user) {
        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getCreatedAt()
        );
    }
}

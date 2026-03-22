package com.feedintelligence.user;

import com.feedintelligence.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> AppException.notFound("User not found"));
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (request.digestHour() != null) {
            user.setDigestHour(request.digestHour());
        }

        return UserResponse.from(userRepository.save(user));
    }
}
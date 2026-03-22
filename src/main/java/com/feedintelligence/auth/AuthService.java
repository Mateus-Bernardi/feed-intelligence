package com.feedintelligence.auth;

import com.feedintelligence.auth.dto.*;
import com.feedintelligence.common.exception.AppException;
import com.feedintelligence.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Verifica se email já existe
        if (userRepository.existsByEmail(request.email())) {
            throw AppException.conflict("Email already registered: " + request.email());
        }

        // Cria o usuário
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return generateTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Busca usuário pelo email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> AppException.unauthorized("Invalid email or password"));

        // Verifica a senha
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AppException.unauthorized("Invalid email or password");
        }

        if (!user.isActive()) {
            throw AppException.unauthorized("Account is disabled");
        }

        log.info("User logged in: {}", user.getEmail());
        return generateTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        // Busca o refresh token no banco
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() -> AppException.unauthorized("Invalid refresh token"));

        // Valida se ainda é utilizável
        if (!refreshToken.isValid()) {
            throw AppException.unauthorized("Refresh token is expired or revoked");
        }

        // Revoga o token atual (rotação de tokens — mais seguro)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Gera novo par de tokens
        User user = refreshToken.getUser();
        log.info("Token refreshed for user: {}", user.getEmail());
        return generateTokenPair(user);
    }

    @Transactional
    public void logout(UUID userId) {
        // Revoga todos os refresh tokens do usuário
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User logged out, all tokens revoked for userId: {}", userId);
    }

    // Gera accessToken + refreshToken e salva o refresh no banco
    private AuthResponse generateTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getRole().name());

        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(refreshTokenExpirationMs / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, refreshTokenValue,
                accessTokenExpirationMs);
    }
}
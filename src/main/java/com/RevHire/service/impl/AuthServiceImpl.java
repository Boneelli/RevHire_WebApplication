package com.RevHire.service.impl;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.RevHire.entity.User;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.AuthService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LogManager.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(User user) {
        logger.info("Attempting to register user with email: {}", user.getEmail());

        if (userRepository.existsByEmail(user.getEmail())) {
            logger.warn("Registration failed - Email already registered: {}", user.getEmail());
            throw new RuntimeException("Email already registered");
        }

        final String hashedPassword = passwordEncoder.encode(user.getPasswordHash());
        user.setPasswordHash(hashedPassword);

        final User savedUser = userRepository.save(user);
        logger.info("User registered successfully with id: {}", savedUser.getUserId());

        return savedUser;
    }

    @Override
    public User login(final String email, final String password) {
        logger.info("Login attempt for email: {}", email);

        final Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            logger.warn("Login failed - User not found for email: {}", email);
            throw new RuntimeException("Invalid credentials");
        }

        final User user = optionalUser.get();
        final String storedPassword = user.getPasswordHash();

        if (storedPassword.startsWith("$2a") || storedPassword.startsWith("$2b")) {
            if (passwordEncoder.matches(password, storedPassword)) {
                logger.info("Login successful (hashed) for email: {}", email);
                return user;
            }
        }
        else if (storedPassword.equals(password)) {
            logger.warn("Plain text password detected for email: {}. Upgrading to hashed.", email);

            final String newHashedPassword = passwordEncoder.encode(password);
            user.setPasswordHash(newHashedPassword);
            userRepository.save(user);

            logger.info("Password upgraded to BCrypt for email: {}", email);
            return user;
        }

        logger.error("Login failed - Invalid password for email: {}", email);
        throw new RuntimeException("Invalid credentials");
    }

    @Override
    public String resetPassword(final String email, final String securityAnswer, final String newPassword) {
        logger.info("Password reset attempt for email: {}", email);

        final User user = userRepository.findByEmail(email).orElseThrow(() -> {
                    logger.error("Password reset failed - User not found: {}", email);
                    return new RuntimeException("User not found");
                });

        final User finalUser = user;
        if(user.getSecurityAnswerHash().equals(securityAnswer)) {
            user.setPasswordHash(newPassword);
            userRepository.save(user);
            logger.info("Password reset successful for user: {}", email);
            return "Password updated successfully";
        }

        logger.warn("Password reset failed - Security answer incorrect for email: {}", email);
        throw new RuntimeException("Security answer incorrect");
    }

    @Override
    public void updatePassword(final Long userId, final String currentPassword, final String newPassword) {
        logger.info("Password update requested for userId: {}", userId);

        final User user = userRepository.findById(userId).orElseThrow(() -> {
                    logger.error("Password update failed - User not found: {}", userId);
                    return new RuntimeException("User session expired or user not found");
                });

        if (!user.getPasswordHash().equals(currentPassword)) {
            logger.warn("Password update failed - Incorrect current password for userId: {}", userId);
            throw new RuntimeException("The current password you entered is incorrect");
        }

        user.setPasswordHash(newPassword);
        userRepository.save(user);
        logger.info("Password updated successfully for userId: {}", userId);
    }

    public void deleteUser(final Long userId) {
        logger.info("Delete user request for userId: {}", userId);

        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            logger.info("User deleted successfully for userId: {}", userId);
        } else {
            logger.error("Delete user failed - User not found for userId: {}", userId);
            throw new RuntimeException("User not found");
        }
    }
}
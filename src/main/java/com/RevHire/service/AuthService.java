package com.RevHire.service;

import com.RevHire.entity.User;

public interface AuthService {

    User registerUser(final User user);

    User login(final String email, final String password);

    String resetPassword(final String email, final String securityAnswer, final String newPassword);

    void updatePassword(final Long userId, final String currentPassword, final String newPassword);

    void deleteUser(final Long userId);
}

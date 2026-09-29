package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser updateDisplayName(Long userId, String displayName) {
        AppUser user = requireUser(userId);
        String normalized = displayName == null ? "" : displayName.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException("显示名称不能为空。");
        if (normalized.length() > 80) throw new IllegalArgumentException("显示名称最多 80 个字符。");
        user.updateDisplayName(normalized);
        return user;
    }

    @Transactional
    public AppUser changePassword(Long userId, String currentPassword,
                                  String newPassword, String confirmation) {
        AppUser user = requireUser(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确。");
        }
        if (newPassword == null || !newPassword.equals(confirmation)) {
            throw new IllegalArgumentException("两次输入的新密码不一致。");
        }
        String validated = PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(validated, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同。");
        }
        user.updatePasswordHash(passwordEncoder.encode(validated));
        return user;
    }

    private AppUser requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在，请重新登录。"));
    }
}
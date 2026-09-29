package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Role;
import com.blogcms.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("必须同时设置 BLOG_ADMIN_USERNAME 和 BLOG_ADMIN_PASSWORD。");
        }
        if (!username.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,39}")) {
            throw new IllegalStateException("初始管理员登录名格式无效。");
        }
        PasswordPolicy.validate(password);
        var existing = users.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new IllegalStateException("初始管理员登录名已被其他角色占用。");
            }
            return;
        }
        users.save(new AppUser(username, passwordEncoder.encode(password), "系统管理员", Role.ADMIN));
    }
}

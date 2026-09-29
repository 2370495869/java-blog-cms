package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Role;
import com.blogcms.repository.UserRepository;
import com.blogcms.web.form.RegistrationForm;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegistrationForm form) {
        String username = form.getUsername().strip().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("这个登录名已经被使用。");
        }

        if (!form.getPassword().equals(form.getPasswordConfirmation())) {
            throw new IllegalArgumentException("两次输入的密码不一致。");
        }
        String password = PasswordPolicy.validate(form.getPassword());
        users.saveAndFlush(new AppUser(username, passwordEncoder.encode(password),
                form.getDisplayName().strip(), Role.AUTHOR));
    }
}

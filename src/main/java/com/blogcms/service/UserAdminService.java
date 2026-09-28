package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.repository.UserRepository;
import com.blogcms.web.form.UserForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public java.util.List<AppUser> users() { return users.findAll(); }

    @Transactional
    public void create(UserForm form) {
        String username = form.getUsername().strip();
        if (users.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("这个登录名已经被使用。");
        String password = PasswordPolicy.validate(form.getPassword());
        users.save(new AppUser(username, passwordEncoder.encode(password),
                form.getDisplayName().strip(), form.getRole()));
    }
}

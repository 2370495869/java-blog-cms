package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Role;
import com.blogcms.repository.UserRepository;
import com.blogcms.web.form.UserForm;
import java.util.Locale;
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
    public void changeEnabled(Long targetId, boolean enabled, Long actorId) {
        AppUser target = users.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在。"));
        if (!enabled && target.getId().equals(actorId)) {
            throw new IllegalArgumentException("不能停用当前登录的账号。");
        }
        if (target.isEnabled() == enabled) return;

        if (!enabled && target.getRole() == Role.ADMIN
                && users.findByRoleAndEnabledTrueOrderByIdAsc(Role.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("不能停用最后一个启用的管理员。");
        }

        target.setEnabled(enabled);
        users.saveAndFlush(target);
    }

    @Transactional
    public void changeRole(Long targetId, Role role, Long actorId) {
        AppUser target = users.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在。"));
        if (target.getId().equals(actorId)) {
            throw new IllegalArgumentException("不能修改当前登录账号的角色。");
        }
        if (role == null) throw new IllegalArgumentException("请选择有效角色。");
        if (target.getRole() == role) return;

        if (target.isEnabled() && target.getRole() == Role.ADMIN && role != Role.ADMIN
                && users.findByRoleAndEnabledTrueOrderByIdAsc(Role.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("不能降级最后一个启用的管理员。");
        }

        target.updateRole(role);
        users.saveAndFlush(target);
    }
    @Transactional
    public void create(UserForm form) {
        String username = form.getUsername().strip().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("这个登录名已经被使用。");
        String password = PasswordPolicy.validate(form.getPassword());
        users.saveAndFlush(new AppUser(username, passwordEncoder.encode(password),
                form.getDisplayName().strip(), form.getRole()));
    }
}

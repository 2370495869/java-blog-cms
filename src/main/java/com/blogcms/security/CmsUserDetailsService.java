package com.blogcms.security;

import com.blogcms.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CmsUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public CmsUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsernameIgnoreCase(username)
                .map(CmsUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
}

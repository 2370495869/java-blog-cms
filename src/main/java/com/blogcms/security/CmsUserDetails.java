package com.blogcms.security;

import com.blogcms.domain.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class CmsUserDetails implements UserDetails {
    private final AppUser user;

    public CmsUserDetails(AppUser user) { this.user = user; }

    public AppUser getUser() { return user; }
    public Long getId() { return user.getId(); }
    public String getDisplayName() { return user.getDisplayName(); }
    public String getRoleName() { return user.getRole().name(); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return user.isEnabled(); }
}

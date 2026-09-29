package com.blogcms.repository;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Role;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findByRoleAndEnabledTrueOrderByIdAsc(Role role);
}

package com.blogcms.repository;

import com.blogcms.domain.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlug(String slug);
}

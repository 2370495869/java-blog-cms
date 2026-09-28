package com.blogcms.repository;

import com.blogcms.domain.ArticleRevision;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, Long> {
    @EntityGraph(attributePaths = "changedBy")
    List<ArticleRevision> findByArticleIdOrderByRevisionNoDesc(Long articleId);
    long countByArticleId(Long articleId);
}

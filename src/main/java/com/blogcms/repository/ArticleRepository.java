package com.blogcms.repository;

import com.blogcms.domain.Article;
import com.blogcms.domain.ArticleStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    @Modifying(flushAutomatically = true)
    @Query("update Article a set a.category = null where a.category.id = :categoryId")
    int clearCategoryReference(@Param("categoryId") Long categoryId);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from article_tags where tag_id = :tagId", nativeQuery = true)
    int clearTagReferences(@Param("tagId") Long tagId);

    @Override
    @EntityGraph(attributePaths = {"author", "category", "tags"})
    java.util.Optional<Article> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.id = :id")
    Optional<Article> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            select distinct a from Article a
            left join a.category c
            left join a.tags t
            where a.status = :status
              and (:keyword = '' or lower(a.title) like lower(concat('%', :keyword, '%'))
                   or lower(a.summary) like lower(concat('%', :keyword, '%'))
                   or lower(a.contentMarkdown) like lower(concat('%', :keyword, '%')))
              and (:categorySlug = '' or c.slug = :categorySlug)
              and (:tagSlug = '' or t.slug = :tagSlug)
            """)
    Page<Article> searchPublished(@Param("status") ArticleStatus status,
                                  @Param("keyword") String keyword,
                                  @Param("categorySlug") String categorySlug,
                                  @Param("tagSlug") String tagSlug,
                                  Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category", "tags"})
    Optional<Article> findBySlugAndStatus(String slug, ArticleStatus status);

    @EntityGraph(attributePaths = {"author", "category", "tags"})
    List<Article> findByAuthorIdOrderByUpdatedAtDesc(Long authorId);

    @EntityGraph(attributePaths = {"author", "category", "tags"})
    List<Article> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"author", "category", "tags"})
    List<Article> findByStatusOrderByUpdatedAtAsc(ArticleStatus status);

    boolean existsBySlug(String slug);
}

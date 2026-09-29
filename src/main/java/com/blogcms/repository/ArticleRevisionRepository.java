package com.blogcms.repository;

import com.blogcms.domain.ArticleRevision;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRevisionRepository extends JpaRepository<ArticleRevision, Long> {
    @EntityGraph(attributePaths = {"changedBy", "tagsSnapshot"})
    List<ArticleRevision> findByArticleIdOrderByRevisionNoDesc(Long articleId);

    @EntityGraph(attributePaths = "changedBy")
    java.util.Optional<ArticleRevision> findFirstByArticleIdAndStatusOrderByRevisionNoDesc(
            Long articleId, String status);

    @Query("""
            select r.article.id as articleId, r.changeNote as changeNote
            from ArticleRevision r
            where r.article.id in :articleIds
              and r.status = 'REJECTED'
              and r.revisionNo = (
                  select max(latest.revisionNo)
                  from ArticleRevision latest
                  where latest.article.id = r.article.id
              )
            """)
    List<RejectionNote> findLatestRejectionNotes(@Param("articleIds") Collection<Long> articleIds);

    long countByArticleId(Long articleId);

    interface RejectionNote {
        Long getArticleId();
        String getChangeNote();
    }
}

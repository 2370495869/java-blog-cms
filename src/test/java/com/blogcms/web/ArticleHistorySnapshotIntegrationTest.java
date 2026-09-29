package com.blogcms.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Article;
import com.blogcms.domain.ArticleRevision;
import com.blogcms.domain.Role;
import com.blogcms.domain.Tag;
import com.blogcms.repository.ArticleRepository;
import com.blogcms.repository.ArticleRevisionRepository;
import com.blogcms.repository.TagRepository;
import com.blogcms.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-history-snapshot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ArticleHistorySnapshotIntegrationTest {
    @Autowired UserRepository users;
    @Autowired TagRepository tags;
    @Autowired ArticleRepository articles;
    @Autowired ArticleRevisionRepository revisions;

    @Test
    void revisionKeepsTheTagNameEvenWhenTheLiveTagIsRenamed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser author = users.saveAndFlush(new AppUser("history_" + suffix, "test-hash", "History Author", Role.AUTHOR));
        Tag tag = tags.saveAndFlush(new Tag("原始标签", "history-tag-" + suffix));
        Article article = new Article("带标签文章", "history-article-" + suffix, "", "正文", author);
        article.update(article.getTitle(), article.getSummary(), article.getContentMarkdown(), null, Set.of(tag));
        articles.saveAndFlush(article);
        revisions.saveAndFlush(new ArticleRevision(article, 1, author, "创建文章"));

        tag.renameTo("重命名后的标签");
        tags.saveAndFlush(tag);
        List<ArticleRevision> history = revisions.findByArticleIdOrderByRevisionNoDesc(article.getId());

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().isTagsSnapshotAvailable()).isTrue();
        assertThat(history.getFirst().getTagsSnapshot()).containsExactly("原始标签");
    }
}

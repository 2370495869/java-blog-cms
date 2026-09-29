package com.blogcms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Article;
import com.blogcms.domain.Category;
import com.blogcms.domain.Role;
import com.blogcms.domain.Tag;
import com.blogcms.repository.ArticleRepository;
import com.blogcms.repository.CategoryRepository;
import com.blogcms.repository.TagRepository;
import com.blogcms.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-taxonomy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.site-title=墨页博客"
})
class TaxonomyManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired TagRepository tags;
    @Autowired ArticleRepository articles;
    @Autowired UserRepository users;

    @Test
    void administratorCanRenameTaxonomyAndConflictsAreExplained() throws Exception {
        String suffix = suffix();
        Category category = categories.saveAndFlush(new Category("Engineering " + suffix, "eng-" + suffix));
        Category otherCategory = categories.saveAndFlush(new Category("Writing " + suffix, "write-" + suffix));
        Tag tag = tags.saveAndFlush(new Tag("security-" + suffix, "sec-" + suffix));
        Tag otherTag = tags.saveAndFlush(new Tag("release-" + suffix, "rel-" + suffix));
        String categorySlug = category.getSlug();
        String tagSlug = tag.getSlug();

        mvc.perform(get("/manage/taxonomy").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/manage/taxonomy/categories/" + category.getId() + "/rename")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/manage/taxonomy/tags/" + tag.getId() + "/delete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("确认删除分类")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("确认删除标签")));
        mvc.perform(post("/manage/taxonomy/categories/{id}/rename", category.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", otherCategory.getName().toLowerCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "这个分类名称已经存在。请换一个名称。"));
        mvc.perform(post("/manage/taxonomy/tags/{id}/rename", tag.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", otherTag.getName().toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "这个标签名称已经存在。请换一个名称。"));

        mvc.perform(post("/manage/taxonomy/categories/{id}/rename", category.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " 工程实践 " + suffix))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage/taxonomy"));
        mvc.perform(post("/manage/taxonomy/tags/{id}/rename", tag.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "安全-" + suffix))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage/taxonomy"));

        assertThat(categories.findById(category.getId()).orElseThrow().getName()).isEqualTo("工程实践 " + suffix);
        assertThat(categories.findById(category.getId()).orElseThrow().getSlug()).isEqualTo(categorySlug);
        assertThat(tags.findById(tag.getId()).orElseThrow().getName()).isEqualTo("安全-" + suffix);
        assertThat(tags.findById(tag.getId()).orElseThrow().getSlug()).isEqualTo(tagSlug);
    }

    @Test
    void deletingCategoryKeepsArticlesUncategorizedAndDeletingTagOnlyRemovesItsAssociation() throws Exception {
        String suffix = suffix();
        Category category = categories.saveAndFlush(new Category("category-" + suffix, "category-" + suffix));
        Tag removedTag = tags.saveAndFlush(new Tag("remove-" + suffix, "remove-" + suffix));
        Tag retainedTag = tags.saveAndFlush(new Tag("retain-" + suffix, "retain-" + suffix));
        AppUser author = users.saveAndFlush(new AppUser("taxonomy_" + suffix, "unused", "Taxonomy test", Role.AUTHOR));
        Article article = new Article("Taxonomy article " + suffix, "taxonomy-" + suffix, "summary", "body", author);
        article.update(article.getTitle(), article.getSummary(), article.getContentMarkdown(), category,
                Set.of(removedTag, retainedTag));
        Long articleId = articles.saveAndFlush(article).getId();

        mvc.perform(post("/manage/taxonomy/categories/{id}/delete", category.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "分类已删除，关联文章已保留并设为未分类。"));

        Article afterCategoryDelete = articles.findById(articleId).orElseThrow();
        assertThat(afterCategoryDelete.getCategory()).isNull();
        assertThat(afterCategoryDelete.getTitle()).isEqualTo("Taxonomy article " + suffix);
        assertThat(afterCategoryDelete.getTags()).extracting("id")
                .containsExactlyInAnyOrder(removedTag.getId(), retainedTag.getId());
        assertThat(categories.findById(category.getId())).isEmpty();

        mvc.perform(post("/manage/taxonomy/tags/{id}/delete", removedTag.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "标签已删除，关联文章及其余内容均已保留。"));

        Article afterTagDelete = articles.findById(articleId).orElseThrow();
        assertThat(afterTagDelete.getTags()).extracting("id").containsExactly(retainedTag.getId());
        assertThat(articles.findById(articleId)).isPresent();
        assertThat(tags.findById(removedTag.getId())).isEmpty();
    }

    @Test
    void taxonomyMutationsRemainAdminOnlyAndRequireCsrf() throws Exception {
        Category category = categories.saveAndFlush(new Category("protected-" + suffix(), "protected-" + suffix()));

        mvc.perform(post("/manage/taxonomy/categories/{id}/delete", category.getId())
                        .with(user("author").roles("AUTHOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/manage/taxonomy/categories/{id}/delete", category.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        assertThat(categories.findById(category.getId())).isPresent();
    }

    private String suffix() { return UUID.randomUUID().toString().substring(0, 8); }
}

package com.blogcms.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Article;
import com.blogcms.domain.ArticleStatus;
import com.blogcms.domain.Role;
import com.blogcms.repository.ArticleRepository;
import com.blogcms.repository.ArticleRevisionRepository;
import com.blogcms.repository.CategoryRepository;
import com.blogcms.repository.TagRepository;
import com.blogcms.repository.UserRepository;
import com.blogcms.security.CmsUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.site-title=墨页博客"
})
class BlogWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    @Autowired ArticleRevisionRepository revisions;
    @Autowired CategoryRepository categories;
    @Autowired TagRepository tags;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void visitorsCanReadButCannotOpenManagement() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("墨页博客")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("好内容")));
        mvc.perform(get("/manage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authorEditorWorkflowKeepsDraftPrivateAndRecordsRevisions() throws Exception {
        CmsUserDetails author = createUser(Role.AUTHOR, "writer");
        CmsUserDetails editor = createUser(Role.EDITOR, "editor");
        CmsUserDetails admin = createUser(Role.ADMIN, "admin");
        CmsUserDetails otherAuthor = createUser(Role.AUTHOR, "other");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String title = "Markdown 安全检查 " + suffix;
        mvc.perform(get("/manage/taxonomy").with(user(admin))).andExpect(status().isOk());
        mvc.perform(post("/manage/taxonomy/categories").with(user(admin)).with(csrf())
                        .param("name", "工程实践"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/manage/taxonomy/tags").with(user(admin)).with(csrf())
                        .param("name", "安全"))
                .andExpect(status().is3xxRedirection());
        Long categoryId = categories.findAllByOrderByNameAsc().getFirst().getId();
        Long tagId = tags.findAllByOrderByNameAsc().getFirst().getId();

        mvc.perform(get("/manage/articles/new").with(user(author))).andExpect(status().isOk());
        mvc.perform(get("/manage/users").with(user(author))).andExpect(status().isForbidden());
        mvc.perform(get("/manage/users").with(user(admin))).andExpect(status().isOk());

        String editorDraftTitle = "编辑草稿 " + suffix;
        mvc.perform(post("/manage/articles").with(user(editor)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", editorDraftTitle)
                        .param("contentMarkdown", "编辑自己的草稿。")
                        .param("action", "draft"))
                .andExpect(status().is3xxRedirection());
        Article editorDraft = articles.findAllByOrderByUpdatedAtDesc().stream()
                .filter(row -> row.getTitle().equals(editorDraftTitle)).findFirst().orElseThrow();
        mvc.perform(get("/manage").with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/manage/articles/" + editorDraft.getId() + "/edit")));
        mvc.perform(get("/manage/articles/" + editorDraft.getId() + "/edit").with(user(editor)))
                .andExpect(status().isOk());

        mvc.perform(post("/manage/articles").with(user(author)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", title)
                        .param("summary", "用于验证草稿、审核和发布流程。")
                        .param("contentMarkdown", "正文包含原始 HTML：<script>alert(1)</script>")
                        .param("categoryId", categoryId.toString())
                        .param("tagIds", tagId.toString())
                        .param("action", "review"))
                .andExpect(status().is3xxRedirection());

        Article article = articles.findAllByOrderByUpdatedAtDesc().stream()
                .filter(row -> row.getTitle().equals(title)).findFirst().orElseThrow();
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
        assertThat(article.getCategory().getName()).isEqualTo("工程实践");
        assertThat(article.getTags()).extracting("name").contains("安全");
        assertThat(revisions.countByArticleId(article.getId())).isEqualTo(2);

        mvc.perform(get("/articles/" + article.getSlug())).andExpect(status().isNotFound());
        mvc.perform(get("/manage/articles/" + article.getId() + "/edit").with(user(otherAuthor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/manage/review").with(user(editor))).andExpect(status().isOk());
        mvc.perform(get("/manage/review/" + article.getId()).with(user(editor))).andExpect(status().isOk());
        mvc.perform(post("/manage/review/" + article.getId() + "/publish")
                        .with(user(author)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);

        mvc.perform(post("/manage/review/" + article.getId() + "/reject").with(user(editor)).with(csrf())
                        .param("note", "请补充参考资料。"))
                .andExpect(status().is3xxRedirection());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.REJECTED);
        mvc.perform(get("/manage/articles/" + article.getId() + "/edit").with(user(author)))
                .andExpect(status().isOk());
        mvc.perform(get("/manage/articles/" + article.getId() + "/history").with(user(admin)))
                .andExpect(status().isOk());

        mvc.perform(post("/manage/articles/" + article.getId()).with(user(author)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", title)
                        .param("summary", "用于验证草稿、审核和发布流程。")
                        .param("contentMarkdown", "更新后的内容。")
                        .param("categoryId", categoryId.toString())
                        .param("tagIds", tagId.toString())
                        .param("action", "review"))
                .andExpect(status().is3xxRedirection());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);

        mvc.perform(post("/manage/review/" + article.getId() + "/publish")
                        .with(user(editor)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(revisions.countByArticleId(article.getId())).isEqualTo(5);

        mvc.perform(get("/").param("q", suffix).param("category", categories.findAllByOrderByNameAsc().getFirst().getSlug())
                        .param("tag", tags.findAllByOrderByNameAsc().getFirst().getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(title)));
        mvc.perform(get("/articles/" + article.getSlug()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }

    private CmsUserDetails createUser(Role role, String prefix) {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        String unpersistedTestPassword = UUID.randomUUID().toString() + UUID.randomUUID();
        AppUser user = users.saveAndFlush(new AppUser(username, passwordEncoder.encode(unpersistedTestPassword),
                prefix + " test", role));
        return new CmsUserDetails(user);
    }
}

package com.blogcms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Article;
import com.blogcms.domain.ArticleStatus;
import com.blogcms.domain.Role;
import com.blogcms.repository.ArticleRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-article-tools;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.site-title=墨页博客"
})
class ArticleAuthorToolsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ArticleRepository articles;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void previewsUnsavedMarkdownThroughTheSafeRendererAndRequiresCsrf() throws Exception {
        CmsUserDetails author = createUser(Role.AUTHOR, "preview-author");
        String title = "未保存预览 " + suffix();
        String body = "正文 **加粗** <script>alert(1)</script>";

        mvc.perform(post("/manage/articles/preview").with(user(author)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", title)
                        .param("summary", "仅预览的摘要")
                        .param("contentMarkdown", body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("<strong>加粗</strong>")))
                .andExpect(content().string(not(containsString("<script>"))));

        mvc.perform(post("/manage/articles/preview").with(user(author))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", title).param("contentMarkdown", body))
                .andExpect(status().isForbidden());
        assertThat(articles.findAllByOrderByUpdatedAtDesc()).noneMatch(article -> title.equals(article.getTitle()));
    }

    @Test
    void showsTheLatestRejectionNoteToItsAuthorOnly() throws Exception {
        CmsUserDetails author = createUser(Role.AUTHOR, "reject-author");
        CmsUserDetails editor = createUser(Role.EDITOR, "reject-editor");
        CmsUserDetails otherAuthor = createUser(Role.AUTHOR, "other-author");
        String title = "退稿可见性 " + suffix();
        String note = "请补充来源并核对数据。";
        Article article = submitForReview(author, title, "初始内容");

        mvc.perform(post("/manage/review/{id}/reject", article.getId()).with(user(editor)).with(csrf())
                        .param("note", note))
                .andExpect(status().is3xxRedirection());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.REJECTED);

        mvc.perform(get("/manage").with(user(author)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString(note)));
        mvc.perform(get("/manage").with(user(otherAuthor)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(note))));
    }

    @Test
    void publishedArticleEditsGoOfflineUntilAnEditorPublishesAgain() throws Exception {
        CmsUserDetails author = createUser(Role.AUTHOR, "published-owner");
        CmsUserDetails editor = createUser(Role.EDITOR, "published-editor");
        CmsUserDetails admin = createUser(Role.ADMIN, "published-admin");
        String title = "已发布后编辑 " + suffix();
        String original = "仍在公开的原始内容";
        String draft = "保存后应下线的草稿内容";
        String submitted = "重新送审的更新内容";
        Article article = submitForReview(author, title, original);
        publish(article, editor);

        mvc.perform(get("/manage/articles/{id}/edit", article.getId()).with(user(author)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("保存草稿或重新送审后会立即从公开页面下线")));
        mvc.perform(get("/manage/articles/{id}/edit", article.getId()).with(user(admin)))
                .andExpect(status().isOk());

        saveArticle(article, author, draft, "draft");
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.DRAFT);
        mvc.perform(get("/articles/{slug}", article.getSlug())).andExpect(status().isNotFound());
        mvc.perform(get("/manage").with(user(author)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/manage/articles/" + article.getId() + "/edit")));

        saveArticle(article, author, submitted, "review");
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
        mvc.perform(get("/articles/{slug}", article.getSlug())).andExpect(status().isNotFound());
        publish(article, editor);

        mvc.perform(get("/articles/{slug}", article.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(submitted)))
                .andExpect(content().string(not(containsString(original))));

        String adminSubmission = "管理员代改并送审的内容";
        saveArticle(article, admin, adminSubmission, "review");
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
        mvc.perform(get("/manage/review/{id}", article.getId()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("你是最近一次提交审核的操作者")))
                .andExpect(content().string(not(containsString("/manage/review/" + article.getId() + "/publish"))));
        mvc.perform(post("/manage/review/{id}/publish", article.getId()).with(user(admin)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/manage/review/{id}/reject", article.getId()).with(user(admin)).with(csrf())
                        .param("note", "管理员本人不能退回这次提交。"))
                .andExpect(status().isForbidden());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
        publish(article, editor);
        mvc.perform(get("/articles/{slug}", article.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(adminSubmission)))
                .andExpect(content().string(not(containsString(submitted))));
    }

    @Test
    void editorAndAdminCannotReviewTheirOwnSubmissions() throws Exception {
        CmsUserDetails editorAuthor = createUser(Role.EDITOR, "editor-author");
        CmsUserDetails adminAuthor = createUser(Role.ADMIN, "admin-author");
        CmsUserDetails independentEditor = createUser(Role.EDITOR, "independent-editor");

        Article editorArticle = submitForReview(editorAuthor, "编辑自审 " + suffix(), "编辑内容");
        assertCannotReviewOwnSubmission(editorArticle, editorAuthor);
        mvc.perform(get("/manage/review/{id}", editorArticle.getId()).with(user(editorAuthor)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("作者不能审核自己的投稿")))
                .andExpect(content().string(not(containsString("/manage/review/" + editorArticle.getId() + "/publish"))));
        mvc.perform(post("/manage/review/{id}/reject", editorArticle.getId()).with(user(independentEditor)).with(csrf())
                        .param("note", "由其他编辑完成审核。"))
                .andExpect(status().is3xxRedirection());

        Article adminArticle = submitForReview(adminAuthor, "管理员自审 " + suffix(), "管理员内容");
        assertCannotReviewOwnSubmission(adminArticle, adminAuthor);
        assertThat(articles.findById(editorArticle.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.REJECTED);
        assertThat(articles.findById(adminArticle.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
    }

    private void assertCannotReviewOwnSubmission(Article article, CmsUserDetails authorReviewer) throws Exception {
        mvc.perform(post("/manage/review/{id}/publish", article.getId()).with(user(authorReviewer)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/manage/review/{id}/reject", article.getId()).with(user(authorReviewer)).with(csrf())
                        .param("note", "作者不能给自己退稿。"))
                .andExpect(status().isForbidden());
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.IN_REVIEW);
    }

    private Article submitForReview(CmsUserDetails author, String title, String content) throws Exception {
        mvc.perform(post("/manage/articles").with(user(author)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", title).param("summary", "集成验证摘要")
                        .param("contentMarkdown", content).param("action", "review"))
                .andExpect(status().is3xxRedirection());
        return articles.findAllByOrderByUpdatedAtDesc().stream()
                .filter(article -> title.equals(article.getTitle())).findFirst().orElseThrow();
    }

    private void publish(Article article, CmsUserDetails editor) throws Exception {
        mvc.perform(post("/manage/review/{id}/publish", article.getId()).with(user(editor)).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private void saveArticle(Article article, CmsUserDetails author, String content, String action) throws Exception {
        mvc.perform(post("/manage/articles/{id}", article.getId()).with(user(author)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", article.getTitle()).param("summary", "已更新摘要")
                        .param("contentMarkdown", content).param("action", action))
                .andExpect(status().is3xxRedirection());
    }

    private CmsUserDetails createUser(Role role, String prefix) {
        String username = prefix + "_" + suffix();
        String unpersistedTestPassword = UUID.randomUUID() + UUID.randomUUID().toString();
        AppUser user = users.saveAndFlush(new AppUser(username, passwordEncoder.encode(unpersistedTestPassword),
                prefix + " test", role));
        return new CmsUserDetails(user);
    }

    private String suffix() { return UUID.randomUUID().toString().substring(0, 8); }
}

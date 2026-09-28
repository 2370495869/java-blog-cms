package com.blogcms.service;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Article;
import com.blogcms.domain.ArticleRevision;
import com.blogcms.domain.ArticleStatus;
import com.blogcms.domain.Category;
import com.blogcms.domain.Role;
import com.blogcms.domain.Tag;
import com.blogcms.repository.ArticleRepository;
import com.blogcms.repository.ArticleRevisionRepository;
import com.blogcms.repository.CategoryRepository;
import com.blogcms.repository.TagRepository;
import com.blogcms.security.CmsUserDetails;
import com.blogcms.web.form.ArticleForm;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleWorkflowService {
    private final ArticleRepository articles;
    private final ArticleRevisionRepository revisions;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final SlugService slugs;

    public ArticleWorkflowService(ArticleRepository articles, ArticleRevisionRepository revisions,
            CategoryRepository categories, TagRepository tags, SlugService slugs) {
        this.articles = articles;
        this.revisions = revisions;
        this.categories = categories;
        this.tags = tags;
        this.slugs = slugs;
    }

    @Transactional(readOnly = true)
    public Page<Article> searchPublished(String keyword, String categorySlug, String tagSlug, Pageable pageable) {
        return articles.searchPublished(ArticleStatus.PUBLISHED, clean(keyword), clean(categorySlug), clean(tagSlug), pageable);
    }

    @Transactional(readOnly = true)
    public Article findPublished(String slug) {
        return articles.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Article> dashboard(CmsUserDetails actor) {
        return actor.getUser().getRole() == Role.AUTHOR
                ? articles.findByAuthorIdOrderByUpdatedAtDesc(actor.getId())
                : articles.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Article> reviewQueue() {
        return articles.findByStatusOrderByUpdatedAtAsc(ArticleStatus.IN_REVIEW);
    }

    @Transactional
    public long create(ArticleForm form, CmsUserDetails actor, boolean submitForReview) {
        requireWriter(actor);
        AppUser author = actor.getUser();
        Article article = new Article(validTitle(form), slugs.uniqueArticleSlug(form.getTitle(), articles),
                value(form.getSummary()), value(form.getContentMarkdown()), author);
        applyTaxonomy(article, form);
        articles.saveAndFlush(article);
        record(article, author, "创建草稿");
        if (submitForReview) {
            article.setStatus(ArticleStatus.IN_REVIEW);
            articles.saveAndFlush(article);
            record(article, author, "提交审核");
        }
        return article.getId();
    }

    @Transactional
    public void save(long articleId, ArticleForm form, CmsUserDetails actor, boolean submitForReview) {
        Article article = editable(articleId, actor);
        if (article.getStatus() != ArticleStatus.DRAFT && article.getStatus() != ArticleStatus.REJECTED) {
            throw new IllegalStateException("这篇文章当前不能编辑。");
        }
        article.update(validTitle(form), value(form.getSummary()), value(form.getContentMarkdown()),
                resolveCategory(form.getCategoryId()), resolveTags(form.getTagIds()));
        article.setStatus(submitForReview ? ArticleStatus.IN_REVIEW : ArticleStatus.DRAFT);
        articles.saveAndFlush(article);
        record(article, actor.getUser(), submitForReview ? "提交审核" : "保存草稿");
    }

    @Transactional(readOnly = true)
    public Article getForEdit(long articleId, CmsUserDetails actor) {
        Article article = requireArticle(articleId);
        assertAuthorOrAdmin(article, actor);
        if (article.getStatus() != ArticleStatus.DRAFT && article.getStatus() != ArticleStatus.REJECTED) {
            throw new IllegalStateException("只有草稿或退回修改的文章可以编辑。");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public Article getForReview(long articleId) {
        Article article = requireArticle(articleId);
        if (article.getStatus() != ArticleStatus.IN_REVIEW) throw new IllegalStateException("这篇文章不在审核队列中。");
        return article;
    }

    @Transactional(readOnly = true)
    public Article getForManagement(long articleId, CmsUserDetails actor) {
        Article article = requireArticle(articleId);
        if (actor.getUser().getRole() == Role.AUTHOR && !article.getAuthor().getId().equals(actor.getId())) {
            throw new AccessDeniedException("只能查看自己创建的文章。");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public List<ArticleRevision> history(long articleId, CmsUserDetails actor) {
        Article article = requireArticle(articleId);
        if (actor.getUser().getRole() == Role.AUTHOR && !article.getAuthor().getId().equals(actor.getId())) {
            throw new AccessDeniedException("不能查看其他作者的修订记录。");
        }
        return revisions.findByArticleIdOrderByRevisionNoDesc(articleId);
    }

    @Transactional
    public void publish(long articleId, CmsUserDetails editor) {
        requireEditor(editor);
        Article article = requireArticle(articleId);
        requireInReview(article);
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(LocalDateTime.now());
        articles.saveAndFlush(article);
        record(article, editor.getUser(), "审核通过并发布");
    }

    @Transactional
    public void reject(long articleId, CmsUserDetails editor, String note) {
        requireEditor(editor);
        String reason = value(note).strip();
        if (reason.isBlank() || reason.length() > 200) throw new IllegalArgumentException("退回说明必填，最多 200 个字符。");
        Article article = requireArticle(articleId);
        requireInReview(article);
        article.setStatus(ArticleStatus.REJECTED);
        articles.saveAndFlush(article);
        record(article, editor.getUser(), reason);
    }

    private Article editable(long id, CmsUserDetails actor) {
        Article article = requireArticle(id);
        assertAuthorOrAdmin(article, actor);
        return article;
    }

    private Article requireArticle(long id) {
        return articles.findById(id).orElseThrow(() -> new IllegalArgumentException("文章不存在。"));
    }

    private void assertAuthorOrAdmin(Article article, CmsUserDetails actor) {
        if (actor.getUser().getRole() != Role.ADMIN && !article.getAuthor().getId().equals(actor.getId())) {
            throw new AccessDeniedException("只能管理自己创建的文章。");
        }
    }

    private void requireWriter(CmsUserDetails actor) {
        if (actor.getUser().getRole() == Role.EDITOR || actor.getUser().getRole() == Role.ADMIN
                || actor.getUser().getRole() == Role.AUTHOR) return;
        throw new AccessDeniedException("当前账号不能撰写文章。");
    }

    private void requireEditor(CmsUserDetails actor) {
        if (actor.getUser().getRole() != Role.EDITOR && actor.getUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("只有编辑或管理员可以审核文章。");
        }
    }

    private void requireInReview(Article article) {
        if (article.getStatus() != ArticleStatus.IN_REVIEW) throw new IllegalStateException("文章已被其他编辑处理。");
    }

    private void record(Article article, AppUser actor, String note) {
        int next = Math.toIntExact(revisions.countByArticleId(article.getId()) + 1);
        revisions.save(new ArticleRevision(article, next, actor, note));
    }

    private void applyTaxonomy(Article article, ArticleForm form) {
        article.update(article.getTitle(), article.getSummary(), article.getContentMarkdown(),
                resolveCategory(form.getCategoryId()), resolveTags(form.getTagIds()));
    }

    private Category resolveCategory(Long id) {
        if (id == null) return null;
        return categories.findById(id).orElseThrow(() -> new IllegalArgumentException("所选分类不存在。"));
    }

    private Set<Tag> resolveTags(List<Long> ids) {
        Set<Long> distinctIds = ids == null ? Set.of() : new HashSet<>(ids);
        List<Tag> found = tags.findAllById(distinctIds);
        if (found.size() != distinctIds.size()) throw new IllegalArgumentException("所选标签中包含无效项目。");
        return new HashSet<>(found);
    }

    private String validTitle(ArticleForm form) {
        String title = value(form.getTitle()).strip();
        if (title.isBlank() || title.length() > 200) throw new IllegalArgumentException("标题不能为空，且最多 200 个字符。");
        return title;
    }

    private String clean(String value) { return value == null ? "" : value.strip(); }
    private String value(String value) { return value == null ? "" : value; }
}

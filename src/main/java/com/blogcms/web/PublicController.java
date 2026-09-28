package com.blogcms.web;

import com.blogcms.domain.Article;
import com.blogcms.domain.Role;
import com.blogcms.security.CmsUserDetails;
import com.blogcms.service.ArticleWorkflowService;
import com.blogcms.service.CatalogService;
import com.blogcms.service.MarkdownRenderer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PublicController {
    private static final int PAGE_SIZE = 9;
    private final ArticleWorkflowService articles;
    private final CatalogService catalog;
    private final MarkdownRenderer markdown;

    public PublicController(ArticleWorkflowService articles, CatalogService catalog, MarkdownRenderer markdown) {
        this.articles = articles;
        this.catalog = catalog;
        this.markdown = markdown;
    }

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String category,
                       @RequestParam(defaultValue = "") String tag,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        String keyword = q == null ? "" : q.strip();
        if (keyword.length() > 100) {
            model.addAttribute("queryError", "搜索词最多 100 个字符。");
            keyword = "";
        }
        int pageNumber = Math.max(0, Math.min(page, 1_000_000));
        Page<Article> result = articles.searchPublished(keyword,
                category == null ? "" : category.strip(), tag == null ? "" : tag.strip(),
                PageRequest.of(pageNumber, PAGE_SIZE,
                        Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        model.addAttribute("articles", result);
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("tags", catalog.tags());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", category == null ? "" : category);
        model.addAttribute("selectedTag", tag == null ? "" : tag);
        return "home";
    }

    @GetMapping("/articles/{slug}")
    public String detail(@PathVariable String slug, Model model,
                         @AuthenticationPrincipal CmsUserDetails viewer) {
        Article article = articles.findPublished(slug);
        if (article == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("article", article);
        model.addAttribute("renderedContent", markdown.render(article.getContentMarkdown()));
        boolean canReadHistory = viewer != null && (viewer.getUser().getRole() != Role.AUTHOR
                || article.getAuthor().getId().equals(viewer.getId()));
        model.addAttribute("canReadHistory", canReadHistory);
        return "article-detail";
    }

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "false") boolean error,
                        @RequestParam(defaultValue = "false") boolean logout,
                        Model model) {
        model.addAttribute("loginError", error);
        model.addAttribute("loggedOut", logout);
        return "login";
    }
}

package com.blogcms.web;

import com.blogcms.domain.Article;
import com.blogcms.security.CmsUserDetails;
import com.blogcms.service.ArticleWorkflowService;
import com.blogcms.service.CatalogService;
import com.blogcms.service.MarkdownRenderer;
import com.blogcms.web.form.ArticleForm;
import jakarta.validation.Valid;
import java.util.ArrayList;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ArticleManagementController {
    private final ArticleWorkflowService articles;
    private final CatalogService catalog;
    private final MarkdownRenderer markdown;

    public ArticleManagementController(ArticleWorkflowService articles, CatalogService catalog,
                                       MarkdownRenderer markdown) {
        this.articles = articles;
        this.catalog = catalog;
        this.markdown = markdown;
    }

    @GetMapping("/manage")
    public String dashboard(@AuthenticationPrincipal CmsUserDetails viewer, Model model) {
        model.addAttribute("articles", articles.dashboard(viewer));
        return "manage/dashboard";
    }

    @GetMapping("/manage/articles/new")
    public String createForm(Model model) {
        model.addAttribute("article", null);
        model.addAttribute("formAction", "/manage/articles");
        prepareForm(model, new ArticleForm());
        return "manage/article-form";
    }

    @PostMapping("/manage/articles")
    public String create(@Valid @ModelAttribute("form") ArticleForm form, BindingResult errors,
                         @RequestParam(defaultValue = "draft") String action,
                         @AuthenticationPrincipal CmsUserDetails viewer,
                         Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            prepareForm(model, form);
            return "manage/article-form";
        }
        try {
            boolean submitForReview = "review".equals(action);
            long id = articles.create(form, viewer, submitForReview);
            redirect.addFlashAttribute("message", "文章已保存。");
            return submitForReview ? "redirect:/manage" : "redirect:/manage/articles/" + id + "/edit";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("formError", e.getMessage());
            prepareForm(model, form);
            return "manage/article-form";
        }
    }

    @GetMapping("/manage/articles/{id}/edit")
    public String editForm(@PathVariable long id, @AuthenticationPrincipal CmsUserDetails viewer,
                           Model model, RedirectAttributes redirect) {
        try {
            Article article = articles.getForEdit(id, viewer);
            ArticleForm form = new ArticleForm();
            form.setTitle(article.getTitle());
            form.setSummary(article.getSummary());
            form.setContentMarkdown(article.getContentMarkdown());
            form.setCategoryId(article.getCategory() == null ? null : article.getCategory().getId());
            form.setTagIds(new ArrayList<>(article.getTags().stream().map(tag -> tag.getId()).toList()));
            model.addAttribute("article", article);
            model.addAttribute("formAction", "/manage/articles/" + id);
            prepareForm(model, form);
            return "manage/article-form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("message", e.getMessage());
            return "redirect:/manage";
        }
    }

    @PostMapping("/manage/articles/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") ArticleForm form,
                        BindingResult errors, @RequestParam(defaultValue = "draft") String action,
                        @AuthenticationPrincipal CmsUserDetails viewer, Model model,
                        RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            try {
                model.addAttribute("article", articles.getForEdit(id, viewer));
                model.addAttribute("formAction", "/manage/articles/" + id);
            }
            catch (IllegalArgumentException | IllegalStateException e) {
                redirect.addFlashAttribute("message", e.getMessage());
                return "redirect:/manage";
            }
            prepareForm(model, form);
            return "manage/article-form";
        }
        try {
            boolean submitForReview = "review".equals(action);
            articles.save(id, form, viewer, submitForReview);
            redirect.addFlashAttribute("message", "文章已保存。");
            return submitForReview ? "redirect:/manage" : "redirect:/manage/articles/" + id + "/edit";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("message", e.getMessage());
            return "redirect:/manage/articles/" + id + "/edit";
        }
    }

    @GetMapping("/manage/articles/{id}/history")
    public String history(@PathVariable long id, @AuthenticationPrincipal CmsUserDetails viewer,
                          Model model) {
        model.addAttribute("article", articles.getForManagement(id, viewer));
        model.addAttribute("revisions", articles.history(id, viewer).stream()
                .map(revision -> new RevisionView(revision, markdown.render(revision.getContentMarkdown())))
                .toList());
        return "manage/history";
    }

    private void prepareForm(Model model, ArticleForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("tags", catalog.tags());
    }
}

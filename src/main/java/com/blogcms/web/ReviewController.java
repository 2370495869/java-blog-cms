package com.blogcms.web;

import com.blogcms.security.CmsUserDetails;
import com.blogcms.service.ArticleWorkflowService;
import com.blogcms.service.MarkdownRenderer;
import com.blogcms.web.form.ReviewForm;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {
    private final ArticleWorkflowService articles;
    private final MarkdownRenderer markdown;

    public ReviewController(ArticleWorkflowService articles, MarkdownRenderer markdown) {
        this.articles = articles;
        this.markdown = markdown;
    }

    @GetMapping("/manage/review")
    public String queue(Model model) {
        model.addAttribute("articles", articles.reviewQueue());
        return "manage/review-queue";
    }

    @GetMapping("/manage/review/{id}")
    public String review(@PathVariable long id, Model model, RedirectAttributes redirect) {
        try {
            var article = articles.getForReview(id);
            model.addAttribute("article", article);
            model.addAttribute("renderedContent", markdown.render(article.getContentMarkdown()));
            model.addAttribute("reviewForm", new ReviewForm());
            return "manage/review-detail";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("message", e.getMessage());
            return "redirect:/manage/review";
        }
    }

    @PostMapping("/manage/review/{id}/publish")
    public String publish(@PathVariable long id, @AuthenticationPrincipal CmsUserDetails viewer,
                          RedirectAttributes redirect) {
        try {
            articles.publish(id, viewer);
            redirect.addFlashAttribute("message", "文章已发布。");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/review";
    }

    @PostMapping("/manage/review/{id}/reject")
    public String reject(@PathVariable long id, @Valid @ModelAttribute("reviewForm") ReviewForm form,
                         BindingResult errors, @AuthenticationPrincipal CmsUserDetails viewer,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "退回说明必填，最多 200 个字符。");
            return "redirect:/manage/review/" + id;
        }
        try {
            articles.reject(id, viewer, form.getNote());
            redirect.addFlashAttribute("message", "文章已退回作者修改。");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/review";
    }
}

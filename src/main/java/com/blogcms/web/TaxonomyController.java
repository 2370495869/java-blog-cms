package com.blogcms.web;

import com.blogcms.service.CatalogService;
import org.springframework.dao.DataIntegrityViolationException;
import com.blogcms.web.form.TaxonomyForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaxonomyController {
    private final CatalogService catalog;

    public TaxonomyController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/manage/taxonomy")
    public String index(Model model) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("tags", catalog.tags());
        model.addAttribute("categoryForm", new TaxonomyForm());
        model.addAttribute("tagForm", new TaxonomyForm());
        return "manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/categories")
    public String addCategory(@Valid @ModelAttribute("categoryForm") TaxonomyForm form,
                              BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "分类名称不能为空，最多 60 个字符。");
        } else {
            try { catalog.addCategory(form.getName()); redirect.addFlashAttribute("message", "分类已添加。"); }
            catch (IllegalArgumentException e) { redirect.addFlashAttribute("message", e.getMessage()); }
            catch (DataIntegrityViolationException e) {
                redirect.addFlashAttribute("message", "分类名称或筛选地址已被占用，请换一个名称。");
            }
        }
        return "redirect:/manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/tags")
    public String addTag(@Valid @ModelAttribute("tagForm") TaxonomyForm form,
                         BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "标签名称不能为空，最多 40 个字符。");
        } else {
            try { catalog.addTag(form.getName()); redirect.addFlashAttribute("message", "标签已添加。"); }
            catch (IllegalArgumentException e) { redirect.addFlashAttribute("message", e.getMessage()); }
            catch (DataIntegrityViolationException e) {
                redirect.addFlashAttribute("message", "标签名称或筛选地址已被占用，请换一个名称。");
            }
        }
        return "redirect:/manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/categories/{id}/rename")
    public String renameCategory(@PathVariable Long id, @Valid @ModelAttribute("categoryForm") TaxonomyForm form,
                                 BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "分类名称不能为空，最多 60 个字符。");
        } else {
            try {
                catalog.renameCategory(id, form.getName());
                redirect.addFlashAttribute("message", "分类名称已更新，原筛选链接仍可使用。");
            } catch (IllegalArgumentException e) {
                redirect.addFlashAttribute("message", e.getMessage().strip());
            } catch (DataIntegrityViolationException e) {
                redirect.addFlashAttribute("message", "分类名称或筛选地址已被占用，请换一个名称。");
            }
        }
        return "redirect:/manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            catalog.deleteCategory(id);
            redirect.addFlashAttribute("message", "分类已删除，关联文章已保留并设为未分类。");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/tags/{id}/rename")
    public String renameTag(@PathVariable Long id, @Valid @ModelAttribute("tagForm") TaxonomyForm form,
                            BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "标签名称不能为空，最多 40 个字符。");
        } else {
            try {
                catalog.renameTag(id, form.getName());
                redirect.addFlashAttribute("message", "标签名称已更新，原筛选链接仍可使用。");
            } catch (IllegalArgumentException e) {
                redirect.addFlashAttribute("message", e.getMessage().strip());
            } catch (DataIntegrityViolationException e) {
                redirect.addFlashAttribute("message", "标签名称或筛选地址已被占用，请换一个名称。");
            }
        }
        return "redirect:/manage/taxonomy";
    }

    @PostMapping("/manage/taxonomy/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            catalog.deleteTag(id);
            redirect.addFlashAttribute("message", "标签已删除，关联文章及其余内容均已保留。");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/taxonomy";
    }
}

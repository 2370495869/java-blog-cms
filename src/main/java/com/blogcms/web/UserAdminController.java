package com.blogcms.web;

import com.blogcms.domain.Role;
import com.blogcms.service.UserAdminService;
import org.springframework.dao.DataIntegrityViolationException;
import com.blogcms.web.form.UserForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.blogcms.security.CmsUserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserAdminController {
    private final UserAdminService users;

    public UserAdminController(UserAdminService users) { this.users = users; }

    @GetMapping("/manage/users")
    public String index(Model model) {
        model.addAttribute("users", users.users());
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", Role.values());
        return "manage/users";
    }

    @PostMapping("/manage/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role,
                             @AuthenticationPrincipal CmsUserDetails actor,
                             RedirectAttributes redirect) {
        try {
            users.changeRole(id, role, actor.getId());
            redirect.addFlashAttribute("message", "账号角色已更新；该账号需要重新登录后使用新权限。");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/users";
    }

    @PostMapping("/manage/users/{id}/enabled")
    public String changeEnabled(@PathVariable Long id, @RequestParam boolean enabled,
                                @AuthenticationPrincipal CmsUserDetails actor,
                                RedirectAttributes redirect) {
        try {
            users.changeEnabled(id, enabled, actor.getId());
            redirect.addFlashAttribute("message", enabled ? "账号已启用。" : "账号已停用。");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/manage/users";
    }

    @PostMapping("/manage/users")
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult errors,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("message", "请检查登录名、显示名称、角色和密码要求。");
        } else {
            try { users.create(form); redirect.addFlashAttribute("message", "账号已创建。"); }
            catch (IllegalArgumentException e) { redirect.addFlashAttribute("message", e.getMessage()); }
            catch (DataIntegrityViolationException e) {
                redirect.addFlashAttribute("message", "这个登录名已经被使用，请换一个。");
            }
        }
        return "redirect:/manage/users";
    }
}

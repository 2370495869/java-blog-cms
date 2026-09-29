package com.blogcms.web;

import com.blogcms.domain.AppUser;
import com.blogcms.security.CmsUserDetails;
import com.blogcms.service.AccountService;
import com.blogcms.web.form.DisplayNameForm;
import com.blogcms.web.form.PasswordChangeForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/account/profile")
    public String profile(Model model) {
        if (!model.containsAttribute("displayForm")) model.addAttribute("displayForm", new DisplayNameForm());
        if (!model.containsAttribute("passwordForm")) model.addAttribute("passwordForm", new PasswordChangeForm());
        return "account/profile";
    }

    @PostMapping("/account/profile/display-name")
    public String updateDisplayName(@Valid @ModelAttribute("displayForm") DisplayNameForm form,
                                    BindingResult errors,
                                    @org.springframework.security.core.annotation.AuthenticationPrincipal CmsUserDetails actor,
                                    Model model, RedirectAttributes redirect,
                                    HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            model.addAttribute("notice", errors.getFieldError("displayName").getDefaultMessage());
            model.addAttribute("noticeError", true);
            return profile(model);
        }
        try {
            AppUser updated = accounts.updateDisplayName(actor.getId(), form.getDisplayName());
            refreshSession(updated, request, response);
            redirect.addFlashAttribute("notice", "显示名称已更新。");
            return "redirect:/account/profile";
        } catch (IllegalArgumentException e) {
            model.addAttribute("notice", e.getMessage());
            model.addAttribute("noticeError", true);
            return profile(model);
        }
    }

    @PostMapping("/account/profile/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordChangeForm form,
                                 BindingResult errors,
                                 @org.springframework.security.core.annotation.AuthenticationPrincipal CmsUserDetails actor,
                                 Model model, RedirectAttributes redirect,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            model.addAttribute("notice", errors.getFieldError().getDefaultMessage());
            model.addAttribute("noticeError", true);
            return profile(model);
        }
        try {
            AppUser updated = accounts.changePassword(actor.getId(), form.getCurrentPassword(),
                    form.getNewPassword(), form.getPasswordConfirmation());
            refreshSession(updated, request, response);
            redirect.addFlashAttribute("notice", "密码已更新；当前会话仍保持登录。");
            return "redirect:/account/profile";
        } catch (IllegalArgumentException e) {
            model.addAttribute("notice", e.getMessage());
            model.addAttribute("noticeError", true);
            return profile(model);
        }
    }

    private void refreshSession(AppUser updated, HttpServletRequest request, HttpServletResponse response) {
        CmsUserDetails principal = new CmsUserDetails(updated);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
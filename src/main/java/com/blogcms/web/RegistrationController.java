package com.blogcms.web;

import com.blogcms.security.CmsUserDetails;
import com.blogcms.service.RegistrationService;
import com.blogcms.web.form.RegistrationForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/register")
    public String form(Model model,
                       @org.springframework.security.core.annotation.AuthenticationPrincipal CmsUserDetails viewer) {
        if (viewer != null) return "redirect:/manage";
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult errors,
                           @org.springframework.security.core.annotation.AuthenticationPrincipal CmsUserDetails viewer,
                           Model model) {
        if (viewer != null) return "redirect:/manage";
        if (errors.hasErrors()) return "register";
        try {
            registration.register(form);
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            String field = e.getMessage().startsWith("两次") ? "passwordConfirmation"
                    : e.getMessage().startsWith("密码") ? "password" : "username";
            errors.rejectValue(field, "registration.invalid", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            errors.rejectValue("username", "registration.conflict", "这个登录名已经被使用，请换一个。");
        }
        model.addAttribute("formError", "注册未完成，请检查输入内容后重试。");
        return "register";
    }
}

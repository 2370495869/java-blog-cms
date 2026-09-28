package com.blogcms.web;

import com.blogcms.security.CmsUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageModelAdvice {
    private final String siteTitle;

    public PageModelAdvice(@Value("${app.site-title:墨页博客}") String siteTitle) {
        this.siteTitle = siteTitle;
    }

    @ModelAttribute
    public void common(Model model, Authentication authentication) {
        model.addAttribute("siteTitle", siteTitle);
        Object principal = authentication == null ? null : authentication.getPrincipal();
        model.addAttribute("viewer", principal instanceof CmsUserDetails user ? user : null);
    }
}

package com.blogcms.service;

import com.blogcms.repository.ArticleRepository;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SlugService {
    public String toSlug(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "article" : normalized;
    }

    public String uniqueArticleSlug(String title, ArticleRepository articles) {
        String base = toSlug(title);
        String stem = base.substring(0, Math.min(base.length(), 195));
        String candidate = stem;
        int suffix = 2;
        while (articles.existsBySlug(candidate)) {
            String tail = "-" + suffix++;
            candidate = stem.substring(0, Math.min(stem.length(), 220 - tail.length())) + tail;
        }
        return candidate;
    }
}

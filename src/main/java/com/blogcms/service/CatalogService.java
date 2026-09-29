package com.blogcms.service;

import com.blogcms.domain.Category;
import com.blogcms.domain.Tag;
import com.blogcms.repository.ArticleRepository;
import com.blogcms.repository.CategoryRepository;
import com.blogcms.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final ArticleRepository articles;
    private final SlugService slugs;

    public CatalogService(CategoryRepository categories, TagRepository tags,
                          ArticleRepository articles, SlugService slugs) {
        this.categories = categories;
        this.tags = tags;
        this.articles = articles;
        this.slugs = slugs;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() { return categories.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public List<Tag> tags() { return tags.findAllByOrderByNameAsc(); }

    @Transactional
    public void addCategory(String rawName) {
        String name = normalize(rawName, 60);
        if (categories.existsByNameIgnoreCase(name)) throw new IllegalArgumentException("这个分类名称已经存在。");
        String slug = uniqueSlug(slugs.toSlug(name), true);
        categories.save(new Category(name, slug));
    }

    @Transactional
    public void addTag(String rawName) {
        String name = normalize(rawName, 40);
        if (tags.existsByNameIgnoreCase(name)) throw new IllegalArgumentException("这个标签名称已经存在。");
        String slug = uniqueSlug(slugs.toSlug(name), false);
        tags.save(new Tag(name, slug));
    }

    @Transactional
    public void renameCategory(Long id, String rawName) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("这个分类不存在，可能已被删除。"));
        String name = normalize(rawName, 60);
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("这个分类名称已经存在。请换一个名称。");
        }
        category.renameTo(name);
    }

    @Transactional
    public void renameTag(Long id, String rawName) {
        Tag tag = tags.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("这个标签不存在，可能已被删除。"));
        String name = normalize(rawName, 40);
        if (tags.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("这个标签名称已经存在。请换一个名称。");
        }
        tag.renameTo(name);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("这个分类不存在，可能已被删除。"));
        articles.clearCategoryReference(id);
        categories.delete(category);
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tags.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("这个标签不存在，可能已被删除。"));
        articles.clearTagReferences(id);
        tags.delete(tag);
    }

    private String uniqueSlug(String base, boolean category) {
        int maxLength = category ? 100 : 80;
        String stem = base.substring(0, Math.min(base.length(), maxLength - 8));
        String candidate = stem;
        int suffix = 2;
        while (category ? categories.existsBySlug(candidate) : tags.existsBySlug(candidate)) {
            String tail = "-" + suffix++;
            candidate = stem.substring(0, Math.min(stem.length(), maxLength - tail.length())) + tail;
        }
        return candidate;
    }

    private String normalize(String raw, int maxLength) {
        String value = raw == null ? "" : raw.strip();
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("名称不能为空或超出长度限制。");
        }
        return value;
    }
}

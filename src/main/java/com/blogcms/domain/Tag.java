package com.blogcms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String name;
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    protected Tag() {}
    public Tag(String name, String slug) { this.name = name; this.slug = slug; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}

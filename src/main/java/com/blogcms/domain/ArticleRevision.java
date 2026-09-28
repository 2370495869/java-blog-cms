package com.blogcms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_revisions", uniqueConstraints =
        @UniqueConstraint(name = "uq_article_revision", columnNames = {"article_id", "revision_no"}))
public class ArticleRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    @Column(name = "revision_no", nullable = false)
    private int revisionNo;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 220)
    private String slug;
    @Column(nullable = false, length = 500)
    private String summary;
    @Column(name = "content_markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentMarkdown;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "category_name", nullable = false, length = 60)
    private String categoryName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private AppUser changedBy;
    @Column(name = "change_note", nullable = false, length = 200)
    private String changeNote;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ArticleRevision() {}

    public ArticleRevision(Article article, int revisionNo, AppUser changedBy, String changeNote) {
        this.article = article;
        this.revisionNo = revisionNo;
        this.title = article.getTitle();
        this.slug = article.getSlug();
        this.summary = article.getSummary();
        this.contentMarkdown = article.getContentMarkdown();
        this.status = article.getStatus().name();
        this.categoryName = article.getCategory() == null ? "" : article.getCategory().getName();
        this.changedBy = changedBy;
        this.changeNote = changeNote;
    }

    @PrePersist
    void beforeInsert() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Article getArticle() { return article; }
    public int getRevisionNo() { return revisionNo; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getSummary() { return summary; }
    public String getContentMarkdown() { return contentMarkdown; }
    public String getStatus() { return status; }
    public String getCategoryName() { return categoryName; }
    public AppUser getChangedBy() { return changedBy; }
    public String getChangeNote() { return changeNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

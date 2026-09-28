package com.blogcms.web;

import com.blogcms.domain.ArticleRevision;

public record RevisionView(ArticleRevision revision, String contentHtml) {
}

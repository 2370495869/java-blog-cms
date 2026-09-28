package com.blogcms.web;

import com.blogcms.domain.ArticleStatus;
import com.blogcms.domain.Role;
import org.springframework.stereotype.Component;

@Component("viewLabels")
public class ViewLabels {
    public String status(ArticleStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case IN_REVIEW -> "待审核";
            case PUBLISHED -> "已发布";
            case REJECTED -> "退回修改";
        };
    }

    public String role(Role role) {
        return switch (role) {
            case AUTHOR -> "作者";
            case EDITOR -> "编辑";
            case ADMIN -> "管理员";
        };
    }
}

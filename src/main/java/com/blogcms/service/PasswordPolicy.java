package com.blogcms.service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static String validate(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("密码至少需要 12 个字符。");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码 UTF-8 编码后不能超过 72 字节。");
        }
        return password;
    }
}

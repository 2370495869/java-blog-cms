package com.blogcms.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeForm {
    @NotBlank(message = "请输入当前密码。")
    private String currentPassword;

    @NotBlank(message = "请输入新密码。")
    @Size(min = 12, message = "新密码至少需要 12 个字符。")
    private String newPassword;

    @NotBlank(message = "请再次输入新密码。")
    private String passwordConfirmation;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getPasswordConfirmation() { return passwordConfirmation; }
    public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
}
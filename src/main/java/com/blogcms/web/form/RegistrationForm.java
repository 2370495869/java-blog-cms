package com.blogcms.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {
    @NotBlank(message = "登录名不能为空。")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,39}",
            message = "登录名须为 3 至 40 位字母、数字、点、下划线或短横线。")
    private String username;

    @NotBlank(message = "显示名称不能为空。")
    @Size(max = 80, message = "显示名称最多 80 个字符。")
    private String displayName;

    @NotBlank(message = "密码不能为空。")
    @Size(min = 12, max = 72, message = "密码需为 12 至 72 个字符。")
    private String password;

    @NotBlank(message = "请再次输入密码。")
    @Size(max = 72, message = "确认密码最多 72 个字符。")
    private String passwordConfirmation;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordConfirmation() { return passwordConfirmation; }
    public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
}

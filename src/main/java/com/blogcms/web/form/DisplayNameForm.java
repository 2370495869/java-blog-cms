package com.blogcms.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DisplayNameForm {
    @NotBlank(message = "显示名称不能为空。")
    @Size(max = 80, message = "显示名称最多 80 个字符。")
    private String displayName;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
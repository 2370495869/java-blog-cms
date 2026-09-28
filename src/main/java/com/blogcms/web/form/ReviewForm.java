package com.blogcms.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReviewForm {
    @NotBlank @Size(max = 200)
    private String note;
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

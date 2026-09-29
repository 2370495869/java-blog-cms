package com.blogcms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blogcms.domain.Role;
import com.blogcms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-registration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.site-title=墨页博客"
})
class RegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registrationIsPublicButPostStillRequiresCsrf() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("注册作者账号")));
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "csrf_writer")
                        .param("displayName", "CSRF 测试")
                        .param("password", "A long valid password 2026")
                        .param("passwordConfirmation", "A long valid password 2026"))
                .andExpect(status().isForbidden());
        assertThat(users.findByUsernameIgnoreCase("csrf_writer")).isEmpty();
    }

    @Test
    void registrationCreatesOnlyAuthorAndAllowsThatUserToLogIn() throws Exception {
        String password = "A long valid password 2026";
        mvc.perform(post("/register").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Writer_New")
                        .param("displayName", "新作者")
                        .param("password", password)
                        .param("passwordConfirmation", password)
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        var user = users.findByUsernameIgnoreCase("writer_new").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.AUTHOR);
        assertThat(user.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches(password, user.getPasswordHash())).isTrue();

        MvcResult login = mvc.perform(post("/login").with(csrf())
                        .param("username", "WRITER_NEW")
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/manage").session(session)).andExpect(status().isOk());
        mvc.perform(get("/register").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"));
        mvc.perform(post("/register").session(session).with(csrf())
                        .param("username", "second_account")
                        .param("displayName", "额外注册账号")
                        .param("password", password)
                        .param("passwordConfirmation", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"));
        assertThat(users.findByUsernameIgnoreCase("second_account")).isEmpty();
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitivelyAndPasswordsMustMatch() throws Exception {
        String password = "A long valid password 2026";
        mvc.perform(post("/register").with(csrf())
                        .param("username", "MixedCase_01")
                        .param("displayName", "第一个作者")
                        .param("password", password)
                        .param("passwordConfirmation", password))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/register").with(csrf())
                        .param("username", "mixedcase_01")
                        .param("displayName", "第二个作者")
                        .param("password", password)
                        .param("passwordConfirmation", password))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("这个登录名已经被使用。")));
        mvc.perform(post("/register").with(csrf())
                        .param("username", "mismatch_01")
                        .param("displayName", "测试作者")
                        .param("password", password)
                        .param("passwordConfirmation", "A different valid password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("两次输入的密码不一致。")));
        assertThat(users.findByUsernameIgnoreCase("mismatch_01")).isEmpty();
    }

    @Test
    void utf8PasswordByteLimitIsEnforced() throws Exception {
        String tooManyBytes = "密".repeat(25);
        mvc.perform(post("/register").with(csrf())
                        .param("username", "unicode_01")
                        .param("displayName", "测试作者")
                        .param("password", tooManyBytes)
                        .param("passwordConfirmation", tooManyBytes))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("72 字节")));
        assertThat(users.findByUsernameIgnoreCase("unicode_01")).isEmpty();
    }
}

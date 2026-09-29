package com.blogcms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blogcms.domain.AppUser;
import com.blogcms.domain.Role;
import com.blogcms.repository.UserRepository;
import com.blogcms.service.UserAdminService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
        "spring.datasource.url=jdbc:h2:mem:blog-account-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.site-title=墨页博客"
})
class AccountLifecycleIntegrationTest {
    private static final String INITIAL_PASSWORD = "Existing secure password 2026";
    private static final String NEW_PASSWORD = "Replacement secure password 2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserAdminService adminUsers;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void personalPageRequiresAuthentication() throws Exception {
        mvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        AppUser author = createUser(Role.AUTHOR, "profile");
        mvc.perform(get("/account/profile").session(login(author.getUsername(), INITIAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("个人账号")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("当前密码")));
    }

    @Test
    void displayNameUpdatePersistsAndRefreshesTheCurrentSessionPrincipal() throws Exception {
        AppUser author = createUser(Role.AUTHOR, "rename");
        MockHttpSession session = login(author.getUsername(), INITIAL_PASSWORD);

        mvc.perform(post("/account/profile/display-name").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("displayName", "  新显示名称  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));

        assertThat(users.findById(author.getId()).orElseThrow().getDisplayName()).isEqualTo("新显示名称");
        mvc.perform(get("/manage").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("新显示名称")));
    }

    @Test
    void passwordChangeChecksOldPasswordConfirmationAndUtf8ByteLimit() throws Exception {
        AppUser author = createUser(Role.AUTHOR, "password");
        MockHttpSession session = login(author.getUsername(), INITIAL_PASSWORD);

        mvc.perform(post("/account/profile/password").session(session).with(csrf())
                        .param("currentPassword", "Wrong current password")
                        .param("newPassword", NEW_PASSWORD)
                        .param("passwordConfirmation", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("当前密码不正确")));
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD,
                users.findById(author.getId()).orElseThrow().getPasswordHash())).isTrue();

        mvc.perform(post("/account/profile/password").session(session).with(csrf())
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("passwordConfirmation", "A different long password 2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("两次输入的新密码不一致")));
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD,
                users.findById(author.getId()).orElseThrow().getPasswordHash())).isTrue();

        String tooManyUtf8Bytes = "密".repeat(25);
        mvc.perform(post("/account/profile/password").session(session).with(csrf())
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", tooManyUtf8Bytes)
                        .param("passwordConfirmation", tooManyUtf8Bytes))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("72 字节")));
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD,
                users.findById(author.getId()).orElseThrow().getPasswordHash())).isTrue();

        mvc.perform(post("/account/profile/password").session(session).with(csrf())
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("passwordConfirmation", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));
        mvc.perform(get("/account/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("当前会话仍保持登录")));
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                users.findById(author.getId()).orElseThrow().getPasswordHash())).isTrue();

        mvc.perform(post("/login").with(csrf())
                        .param("username", author.getUsername())
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").with(csrf())
                        .param("username", author.getUsername())
                        .param("password", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"));
    }

    @Test
    void administratorCanDisableAndReenableAnAccountButNotThemself() throws Exception {
        AppUser admin = createUser(Role.ADMIN, "admin");
        AppUser author = createUser(Role.AUTHOR, "disabled");
        MockHttpSession adminSession = login(admin.getUsername(), INITIAL_PASSWORD);
        MockHttpSession authorSession = login(author.getUsername(), INITIAL_PASSWORD);

        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("停用账号")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("作者")));

        mvc.perform(post("/manage/users/" + author.getId() + "/enabled").session(adminSession).with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage/users"));
        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账号已停用")));
        assertThat(users.findById(author.getId()).orElseThrow().isEnabled()).isFalse();
        mvc.perform(get("/manage").session(authorSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?disabled"));
        assertThat(authorSession.isInvalid()).isTrue();
        mvc.perform(get("/login?disabled"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("此账号已被管理员停用")));
        mvc.perform(post("/login").with(csrf())
                        .param("username", author.getUsername())
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/manage/users/" + author.getId() + "/enabled").session(adminSession).with(csrf())
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账号已启用")));
        assertThat(users.findById(author.getId()).orElseThrow().isEnabled()).isTrue();
        mvc.perform(post("/login").with(csrf())
                        .param("username", author.getUsername())
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"));

        mvc.perform(post("/manage/users/" + admin.getId() + "/enabled").session(adminSession).with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("不能停用当前登录的账号")));
    }

    @Test
    void accountAdministrationIsAdminOnlyAndTheLastEnabledAdminGuardIsEnforced() throws Exception {
        AppUser admin = createUser(Role.ADMIN, "owner");
        AppUser author = createUser(Role.AUTHOR, "ordinary");
        MockHttpSession authorSession = login(author.getUsername(), INITIAL_PASSWORD);

        mvc.perform(post("/manage/users/" + admin.getId() + "/enabled").session(authorSession).with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/manage/users/" + admin.getId() + "/role").session(authorSession).with(csrf())
                        .param("role", "EDITOR"))
                .andExpect(status().isForbidden());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUsers.changeEnabled(admin.getId(), false, -1L))
                .withMessageContaining("最后");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUsers.changeRole(admin.getId(), Role.AUTHOR, -1L))
                .withMessageContaining("降级最后一个启用的管理员");
        assertThat(users.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void administratorCanChangeRolesAndOldRoleSessionsMustRelogin() throws Exception {
        AppUser admin = createUser(Role.ADMIN, "roleadmin");
        AppUser author = createUser(Role.AUTHOR, "roleauthor");
        MockHttpSession authorSession = login(author.getUsername(), INITIAL_PASSWORD);
        MockHttpSession adminSession = login(admin.getUsername(), INITIAL_PASSWORD);

        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("更新角色")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("当前账号角色不可更改")));

        mvc.perform(post("/manage/users/" + author.getId() + "/role").session(adminSession).with(csrf())
                        .param("role", "EDITOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage/users"));
        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账号角色已更新")));

        assertThat(users.findById(author.getId()).orElseThrow().getRole()).isEqualTo(Role.EDITOR);
        mvc.perform(get("/manage").session(authorSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?roleChanged"));
        assertThat(authorSession.isInvalid()).isTrue();
        mvc.perform(get("/login?roleChanged"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账号角色已调整，请重新登录以加载新权限")));

        MockHttpSession updatedSession = login(author.getUsername(), INITIAL_PASSWORD);
        mvc.perform(get("/manage/review").session(updatedSession))
                .andExpect(status().isOk());

        mvc.perform(post("/manage/users/" + admin.getId() + "/role").session(adminSession).with(csrf())
                        .param("role", "AUTHOR"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        mvc.perform(get("/manage/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("不能修改当前登录账号的角色")));
    }

    @Test
    void administratorCreatedUsernamesAreNormalizedToLowercase() throws Exception {
        AppUser admin = createUser(Role.ADMIN, "creator");
        MockHttpSession session = login(admin.getUsername(), INITIAL_PASSWORD);

        mvc.perform(post("/manage/users").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Team_Upper")
                        .param("displayName", "Upper Creator")
                        .param("password", "Team password value 2026")
                        .param("role", "AUTHOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage/users"));

        assertThat(users.findByUsernameIgnoreCase("team_upper"))
                .isPresent()
                .get()
                .extracting(AppUser::getUsername)
                .isEqualTo("team_upper");
    }

    private AppUser createUser(Role role, String prefix) {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(new AppUser(username, passwordEncoder.encode(INITIAL_PASSWORD),
                prefix + " initial", role));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
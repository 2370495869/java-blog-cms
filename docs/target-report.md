# 目标报告：java-blog-cms

## 建设目标

把现有实验四的单文件 HTTP/H2 文章与标签程序扩展为一个可独立运行、适合演示和公开发布的博客内容管理系统。保留原实验源码作为历史参考，新系统提供访客阅读、作者写作、编辑审核和管理员维护各自明确的操作入口。

## 改造前项目起点

- 改造开始时位于 `main` 分支，仓库尚无提交且未配置 Git remote；目录中的文件均是未提交内容，改造时必须保留其有效内容。
- 现有应用只有 Java 内置 HTTP 服务、H2 文件数据库、文章/标签、组合筛选、分页和详情页，没有账户认证、审核发布流程、分类、修订记录或 Markdown 渲染。
- 根目录中已有本机数据库、`.class`、`h2.jar`、日志和 `target/` 构建目录；这些运行产物仅留在本机，不能进入新仓库。
- 改造开始时已安装 Eclipse Temurin Java 21；当时终端未找到 Maven、Docker CLI 或 GitHub CLI。

## 用户可见功能

- 访客可按标题/正文关键词、分类和标签搜索公开文章，并分页浏览；已发布文章使用稳定的友好 URL。
- 访客可自助注册并直接成为作者；作者可新建和编辑自己的草稿、维护分类与标签关系、预览 Markdown、投稿并查看修订记录。
- 编辑可查看待审核文章并发布或退回修改；管理员拥有编辑权限并可创建、停用账号和维护分类/标签。作者修改已发布文章后须重新审核才能公开。
- 未发布内容始终不出现在公开列表、搜索结果或公开详情页中。

## 技术与部署选择

- Java 21 LTS 与 Spring Boot 4.0.8；Boot 4.0.8 官方系统要求为 Java 17 至 26，因此与 Java 21 兼容。
- Maven 3.9.16，由提交到项目的 Maven Wrapper 固定；构建使用 Maven Wrapper 而不依赖系统级 Maven。
- Thymeleaf 服务端页面、Spring Security 表单登录与角色授权、BCrypt 密码哈希、Spring Data JPA 持久化。
- MySQL 8.4 LTS 用于 Docker Compose 演示部署；当前 Compose 固定到经本机验证可拉取和启动的 MySQL 8.4.11；Flyway 版本化迁移管理数据库结构。本机默认使用隔离的 H2 文件数据库，方便不安装 Docker 时启动。
- CommonMark Java 0.30.0（表格扩展）解析 Markdown，渲染时转义原始 HTML 并过滤不安全 URL，再由 jsoup 1.23.2 Safelist 做输出净化。
- 文章状态采用草稿、待审核、已发布和退回修改；修订表保存文章内容、分类和标签名称、状态、修改者、时间和说明的快照。
- GitHub Actions 执行 Wrapper 构建与自动化测试；Dockerfile 和 Compose 提供应用与 MySQL 的一键启动方式。

选型参考：

- [Spring Boot 4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)
- [Spring Boot 构建与官方 Starter](https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html)
- [Spring Security 7.0 参考文档](https://docs.spring.io/spring-security/reference/)
- [MySQL 8.4 发布说明](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/)
- [Apache Maven 3.9.16 发布说明](https://maven.apache.org/docs/3.9.16/release-notes.html)
- [Maven Wrapper 官方文档](https://maven.apache.org/tools/wrapper/)
- [CommonMark Java 渲染与安全选项](https://github.com/commonmark/commonmark-java)
- [jsoup 发布记录](https://jsoup.org/news/)

## 安全边界

- 所有管理写操作需要登录并按角色授权；作者自助注册固定获得 AUTHOR 角色，保留 Spring Security 默认 CSRF 防护，密码只存哈希。停用用户的既有会话在下次访问受保护页面时立即失效。
- Markdown 原始 HTML 不作为可信内容输出；拦截脚本 URL 和不在白名单中的 HTML 元素。
- 数据库密码、初始管理员密码只由环境变量注入；仓库只包含无真实凭据的示例配置。
- 使用 `.gitignore` 排除环境文件、本机数据库、上传目录、日志、编译输出和本地 IDE 配置。
- 注册页面可匿名访问，但只创建启用状态的 AUTHOR；访客不能选择角色，编辑审核是发布的唯一途径。管理员负责创建高权限角色，无法停用自己或最后一个启用的管理员。

## 完成验收标准

1. `./mvnw verify` 可在 Java 21 环境完成编译和自动化测试；测试覆盖 Markdown XSS 防护、角色访问边界和文章审核状态转换。
2. 不依赖 Docker 的本机启动方式可运行，Flyway 可创建数据库结构；`docker compose config` 与 Compose 服务定义可供使用 MySQL 8.4 LTS 部署；当前实测镜像为 8.4.11。
3. 实现公开检索/分页/分类/标签/友好 URL、作者自助注册、个人账号设置、账号启停、草稿与已发布文章复审、编辑审核、分类/标签维护及完整修订记录。
4. 中文 README 说明环境要求、启动、管理员初始化、角色工作流、迁移、测试、Compose、备份和安全配置。
5. 实现报告如实记录通过的检查和无法执行的检查；最终提交前逐项审查文件清单与 diff，不提交凭据、本机数据或构建产物。
6. 仅当前项目进入名为 `java-blog-cms` 的公开 GitHub 仓库，并将提交推送到 `main`，不覆盖未知远程历史。

## 验证计划与已知环境限制

验证方案包括 Wrapper 构建/测试、带 H2 的本机启动、公开页面 smoke check、Markdown 与权限测试、依赖和敏感文件扫描及最终 Git 文件清单审查。2026-09-29 复核时 Docker Desktop 已可用，基于 Compose 固定的 MySQL 8.4.11 完成启动、迁移和 HTTP smoke 验证；本机系统 Maven 未找到，但 Maven Wrapper 验证通过。`gh` 已安装但当前凭据无效；GitHub 内容检查与同步通过当前 GitHub connector 完成。GitHub Actions 状态以对应提交的工作流记录为准；未执行的检查仍在实现报告中标明，不会写成通过。

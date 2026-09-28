# 实现报告：java-blog-cms

## 已完成内容

- 用 Java 21 和 Spring Boot 4.0.8 重建主应用；项目携带 Apache Maven Wrapper 3.3.4 脚本，固定下载 Maven 3.9.16。
- 用 Spring MVC、Thymeleaf、Spring Security、Spring Data JPA 实现公开页面和内容工作台；原实验 `BlogServer.java` 原样保存在 `legacy/`，不再作为主程序运行。
- 访客可以按文章关键词、分类和标签搜索，分页浏览已发布文章，并通过保留 Unicode 的 slug 访问文章。
- 作者只能编辑自己的草稿或退回文章；编辑可审核、发布或退回；管理员可分配三个角色并管理分类和标签。表单沿用 Spring Security CSRF 防护，密码用 BCrypt 哈希。
- 数据迁移由 Flyway 版本化管理；Compose 数据库为 MySQL 8.4.12，本机默认数据库为 Boot BOM 管理的 H2 2.4.240。Hibernate 使用 `ddl-auto=validate`。
- 每次草稿保存、送审、发布和退回都写入文章修订快照。
- Markdown 使用 CommonMark Java 0.30.0 渲染，转义原始 HTML、过滤危险 URL，并使用 jsoup 1.23.2 Safelist 再净化输出。
- 提供 Dockerfile、Docker Compose、忽略规则和中文 README。GitHub Actions 用 Java 21 执行 Wrapper `verify`，Pull Request 检查新增依赖。

## 验证结果

- 在 Eclipse Temurin Java 21.0.11 和 Maven Wrapper 3.9.16 下执行 `./mvnw -B -ntp verify`：通过，5 项测试全部通过，生成 `target/java-blog-cms-1.0.0.jar`。
- 自动化测试覆盖 Markdown 脚本和危险协议过滤、中文页面渲染、匿名访问限制、账号角色边界、文章分类/标签、待审核文章隐藏、退回与重新送审、编辑发布及修订快照。
- 用可执行 JAR 在本机启动并完成 HTTP smoke check：首页和登录页返回 200，健康端点返回 `UP`，中文标题正确，响应包含 Content Security Policy；匿名访问工作台会重定向到登录页。另用临时随机密码验证环境变量创建的初始管理员可通过 CSRF 保护的登录表单登录，并打开工作台和账号管理页。
- 发布前检查了 69 个暂存文件：没有发现私钥/令牌模式、学号或姓名标记，也没有暂存 `.env`、数据库、上传目录、日志或构建产物。
- H2 Flyway 迁移 V1 与 Hibernate schema 校验在测试及实际本机启动时均成功。Flyway 11.14.1 发出提示：其正式验证的 H2 最高版本为 2.3.232，而 Boot BOM 选用 H2 2.4.240；本次迁移和启动实测通过，但 Flyway 尚未声明验证该 H2 补丁版本。

## 尚未验证的部分

- 当前环境没有 Docker CLI，因此未运行 `docker compose config`、Docker 镜像构建或 MySQL 8.4.12 容器启动；MySQL 专属行为仍需在具备 Docker 的环境验证。集成测试和本机启动使用 H2。
- GitHub Actions 尚未在 GitHub runner 上执行；本地用同一 Maven Wrapper 构建和测试。

## GitHub 同步结果

- 已创建公开仓库：[java-blog-cms](https://github.com/2370495869/java-blog-cms)。
- 当前项目的 `main` 分支已通过 GitHub CLI 推送；首次发布的应用提交为 `914b700cb3e0f45f9f3efa483f13708614a12ae9`。随后将本节发布状态补入本报告，并推送报告更新。
- 推送前检查确认暂存的项目文件不含凭据、个人标记、`.env`、本机数据库、上传目录、日志或构建产物；仅操作本项目仓库。

## 运行数据和安全边界

- 本机 H2 文件位于忽略的 `data/` 目录；原实验根目录数据库没有迁移。Compose 数据库使用命名卷 `mysql_data`。
- 初始管理员由 `BLOG_ADMIN_USERNAME`、`BLOG_ADMIN_PASSWORD` 环境变量创建；仓库不附带真实账号凭据，也不开放匿名注册。
- `.gitignore` 排除 `.env`、数据库、上传目录、日志、类文件、JAR 与 Maven 构建目录。Markdown 只在经过安全渲染和净化后作为 HTML 展示。
- 项目尚未指定开源许可证，公开仓库不代表自动授予再分发授权。

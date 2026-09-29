# 实现报告：java-blog-cms

## 已完成内容

- 用 Java 21 和 Spring Boot 4.0.8 重建主应用；项目携带 Apache Maven Wrapper 3.3.4 脚本，固定下载 Maven 3.9.16。
- 用 Spring MVC、Thymeleaf、Spring Security、Spring Data JPA 实现公开页面和内容工作台；原实验 `BlogServer.java` 原样保存在 `legacy/`，不再作为主程序运行。
- 访客可以按文章关键词、分类和标签搜索，分页浏览已发布文章，并通过保留 Unicode 的 slug 访问文章。
- 访客可自助注册成为作者；作者可维护显示名称/密码、撰写和预览文章、修改自己的草稿/退回文章/已发布文章（修改后必须重新审核）。编辑可审核、发布或退回，禁止自审；管理员可创建和启停账号、维护分类和标签。写操作保留 CSRF 防护，密码用 BCrypt 哈希。
- 数据迁移由 Flyway 版本化管理；Compose 数据库固定为 MySQL 8.4.11，本机默认数据库为 Boot BOM 管理的 H2 2.4.240。Hibernate 使用 `ddl-auto=validate`。
- 每次草稿保存、送审、发布和退回都写入文章修订快照，历史保存当时的分类和标签名称；作者工作台展示退回原因。
- Markdown 使用 CommonMark Java 0.30.0 渲染，转义原始 HTML、过滤危险 URL，并使用 jsoup 1.23.2 Safelist 再净化输出。
- 提供 Dockerfile、Docker Compose、忽略规则和中文 README。GitHub Actions 用 Java 21 执行 Wrapper `verify`，随后构建 Compose 服务并检查应用健康状态和 HTTP 200，Pull Request 还检查新增依赖。

## 验证结果

- 在 Eclipse Temurin Java 21.0.11 和 Maven Wrapper 3.9.16 下执行 `.\mvnw.cmd -B -ntp verify`：本次全面完善后 24 项测试全部通过，生成 `target/java-blog-cms-1.0.0.jar`。
- 自动化测试覆盖 Markdown 脚本和危险协议过滤、匿名访问限制、CSRF、注册角色固定与密码边界、账号个人资料/改密/启停/角色变更与会话失效、作者/编辑权限、文章预览/退回原因/复审/作者及最近送审操作者自审边界、分类标签维护和修订标签快照。
- 用可执行 JAR 在本机启动并完成 HTTP smoke check：首页和登录页返回 200，健康端点返回 `UP`，中文标题正确，响应包含 Content Security Policy；匿名访问工作台会重定向到登录页。另用临时随机密码验证环境变量创建的初始管理员可通过 CSRF 保护的登录表单登录，并打开工作台和账号管理页。
- 本轮提交前检查了 51 个暂存文件：凭据特征和个人标识扫描均无命中；暂存清单不含 `.env`、本机数据库、上传目录、日志或构建产物。
- H2 Flyway 迁移 V1–V3 与 Hibernate schema 校验在测试和实际启动时均成功。Flyway 11.14.1 发出提示：其正式验证的 H2 最高版本为 2.3.232，而 Boot BOM 选用 H2 2.4.240；本次迁移和启动实测通过，但 Flyway 尚未声明验证该 H2 补丁版本。

- 2026-09-29 在 Windows Docker Desktop linux/amd64 引擎上用现有 Compose 项目重建应用；MySQL `8.4.11` 健康，Flyway 将保留的数据卷从 V2 升级到 V3。应用容器启动并报告 healthy；宿主机访问 `/`、`/login`、`/register`、`/actuator/health` 均返回 HTTP 200。全量 Maven `verify` 在宿主机完成（24 项测试通过）；Docker 镜像构建运行 `package`，测试由同一轮全量验证负责。重建期间保留了 `mysql_data` 数据卷。

## 验证边界

- Flyway 提示其已验证的 MySQL 版本范围截至 8.1；本次 MySQL 8.4.11 上 V1 迁移成功，但不能据此证明所有 MySQL 8.4 行为。
- Docker 启动验证针对本机 linux/amd64；其他 CPU 架构和生产反向代理配置未在此轮实测。
- GitHub Actions 工作流配置为推送时运行 Maven `verify`；本地 Docker 与 Maven 结果独立记录。

## GitHub 同步结果

- 按要求删除旧远端仓库后，重新创建了公开仓库：[java-blog-cms](https://github.com/2370495869/java-blog-cms)。
- 新 `main` 历史从提交 `a206da940c92e427a42d75166fe2d2263f986735` 开始；该根提交及本报告更新均使用当前 GitHub 登录账号 `2370495869` 的 ID 型 `noreply` 身份。GitHub contributors API 确认贡献者只有该账号，没有 Codex 或其他账号。
- 重建发布前重新检查了 69 个受跟踪文件：未发现敏感凭据模式、`.env`、本机数据库、上传目录、日志或构建产物；只操作了当前项目仓库。

## 运行数据和安全边界

- 本机 H2 文件位于忽略的 `data/` 目录；原实验根目录数据库没有迁移。Compose 数据库使用命名卷 `mysql_data`。
- 初始管理员由 `BLOG_ADMIN_USERNAME`、`BLOG_ADMIN_PASSWORD` 环境变量创建；作者注册公开但固定创建 AUTHOR 角色，不提供匿名高权限账号。仓库不附带真实账号凭据。
- `.gitignore` 排除 `.env`、数据库、上传目录、日志、类文件、JAR 与 Maven 构建目录。Markdown 只在经过安全渲染和净化后作为 HTML 展示。
- 项目尚未指定开源许可证，公开仓库不代表自动授予再分发授权。

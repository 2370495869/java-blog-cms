# 墨页博客 CMS

一个可独立运行的中文博客内容管理系统。访客可以搜索和阅读已发布文章；作者保存草稿并提交审核；编辑决定发布或退回修改；管理员管理账号、分类和标签。

## 功能

- 公开文章列表、标题/摘要/正文搜索、分类与标签筛选、分页和文章友好 URL。
- 作者、编辑、管理员三种角色；登录使用 Spring Security 表单认证和 BCrypt 密码哈希。
- 作者只能编辑自己的草稿或被退回文章；编辑不能替作者改稿，可以发布或退回待审核文章；管理员可创建和分配账号。
- 每次保存、提交审核、发布或退回都会留下修订快照，记录内容、状态、操作者、时间和说明。
- Markdown 支持常见排版和表格。原始 HTML 会转义，危险 URL 会过滤，HTML 输出再经过 jsoup 白名单清理。
- Flyway 版本化数据库迁移；本机默认用 H2 文件库，Compose 使用 MySQL 8.4 LTS。
- Maven Wrapper、Dockerfile、Docker Compose 和 GitHub Actions 构建/依赖变更检查。

## 技术选择

- Java 21、Spring Boot 4.0.8、Maven 3.9.16。
- Spring MVC + Thymeleaf、Spring Security、Spring Data JPA。
- MySQL 8.4.11（Compose，属于 8.4 LTS）、H2（本机快速运行）、Flyway。
- CommonMark Java 0.30.0、jsoup 1.23.2。

## 快速开始

需要 Java 21 或更新版本。Maven Wrapper 会下载固定版本的 Maven；本机不需要预装 Maven。首次启动还需要可访问 Maven Central 下载依赖。

### 本机启动（H2）

Windows PowerShell 7：

```powershell
$env:BLOG_ADMIN_USERNAME = 'admin'
$secret = Read-Host '设置初始管理员密码（至少 12 个字符）' -AsSecureString
$env:BLOG_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
.\mvnw.cmd -B -ntp spring-boot:run
```

macOS/Linux：

```sh
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export PYTHONIOENCODING=utf-8
export BLOG_ADMIN_USERNAME=admin
read -r -s -p '设置初始管理员密码（至少 12 个字符）: ' BLOG_ADMIN_PASSWORD
printf '\n'
export BLOG_ADMIN_PASSWORD
./mvnw -B -ntp spring-boot:run
unset BLOG_ADMIN_PASSWORD
```

打开 <http://127.0.0.1:8080>。登录入口位于 `/login`。本机默认只监听回环地址，H2 数据保存在 `data/blog-cms`；该目录已加入忽略规则。初始管理员仅在首次启动时创建，后续登录名必须保持一致。密码至少 12 个字符，UTF-8 编码后最多 72 字节。

如果不设置管理员环境变量，公开页面仍可运行和浏览；此时没有任何可登录的管理账号。

### Docker Compose（MySQL）

先设置 Compose 所需的数据库和管理员凭据，再启动服务。密码会通过隐藏输入读取：

```powershell
$secret = Read-Host 'MySQL root 密码' -AsSecureString
$env:MYSQL_ROOT_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$secret = Read-Host '博客数据库密码' -AsSecureString
$env:MYSQL_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:BLOG_ADMIN_USERNAME = 'admin'
$secret = Read-Host '初始管理员密码（至少 12 个字符）' -AsSecureString
$env:BLOG_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
docker compose up --build -d
```

Compose 将应用映射到 `127.0.0.1:8080`，数据库只在 Compose 内部网络开放，数据保存在命名卷 `mysql_data`。查看启动日志可运行 `docker compose logs -f app`；正常退出时运行 `docker compose down`，它会保留数据库卷。需要清空数据库时才使用 `docker compose down -v`。

通过 HTTPS 反向代理对外提供服务时，将 `SESSION_COOKIE_SECURE=true`，并按代理方式配置 TLS 和转发头。Compose 默认只供本机使用，不会直接把数据库端口或应用端口开放到公网。

### 运行脚本

- Windows：`run.bat` 或 `run.ps1`
- macOS/Linux：`./run.sh`

也可直接打包后运行：

```sh
./mvnw -B -ntp verify
java -jar target/java-blog-cms-1.0.0.jar
```

## 内容工作流与权限

| 角色 | 能做什么 |
| --- | --- |
| 作者 | 创建文章、编辑自己的草稿或退回文章、保存草稿、提交审核、查看自己的修订记录 |
| 编辑 | 查看审核队列、发布或退回文章、撰写自己的文章、查看文章修订记录 |
| 管理员 | 编辑权限、创建作者/编辑/管理员账号、维护分类和标签 |
| 访客 | 搜索、筛选、分页浏览和阅读已发布文章 |

状态流转：`草稿 → 待审核 → 已发布`，或 `待审核 → 退回修改 → 草稿/重新提交`。退回时必须填写说明。公开查询只读取“已发布”文章。

首次部署的管理员通过 `BLOG_ADMIN_USERNAME` 和 `BLOG_ADMIN_PASSWORD` 初始化。随后在“账号管理”页面创建作者和编辑账号；系统不开放匿名注册。分类与标签由管理员维护，在文章编辑页选择。

## 构建与检查

```sh
./mvnw -B -ntp verify
```

测试涵盖 Markdown 脚本与危险链接过滤、匿名访问限制、作者/编辑发布权限、草稿公开边界和修订记录。GitHub Actions 在推送和 Pull Request 上运行同一构建；Pull Request 还会检查新增依赖。

## 数据库迁移和备份

Flyway 在启动时按 `src/main/resources/db/migration` 中的迁移创建或升级结构；Hibernate 只校验结构，不自动改表。使用 MySQL 的 Compose 部署请定期备份 `mysql_data`。本机 H2 在应用停止后备份整个 `data/` 目录。旧实验 H2 文件不会自动迁移。

连接地址、数据库用户名和密码分别使用 Spring Boot `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 环境变量；只把密钥放在本机未跟踪的环境配置中，不要提交 `.env`。仓库忽略本机数据库、上传目录、日志和构建输出。

## 项目结构

```text
src/main/java/com/blogcms/              应用、领域、仓储、权限、服务和控制器
src/main/resources/db/migration/         Flyway 数据库迁移
src/main/resources/templates/            Thymeleaf 页面
src/main/resources/static/css/           页面样式
src/test/java/                           自动化测试
legacy/BlogServer.java                   原实验单文件程序，原样保留
docs/target-report.md                    目标与验收要求
docs/implementation-report.md            实际实现和验证记录
compose.yaml                             应用与 MySQL 服务
Dockerfile                               非 root 运行的应用镜像
```

项目当前没有指定开源许可证；公开仓库不自动授予复制、修改或分发授权。

package com.shaoyun.java.lab4;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 实验四：博客系统的文章与标签管理
 *
 * 使用 H2 嵌入式数据库（纯Java，一个jar即可），浏览器访问 http://localhost:8082
 *
 * 数据库表（多对多关系）：
 *   articles      — 文章表
 *   tags          — 标签表
 *   article_tags  — 文章-标签关联表
 *
 * 运行方式：
 *   javac -cp ".;h2.jar" BlogServer.java
 *   java -cp ".;h2.jar" BlogServer
 */
public class BlogServer {

    private static final String DB_USER = System.getenv().getOrDefault("BLOG_DB_USER", "sa");
    private static final String DB_PASS = System.getenv().getOrDefault("BLOG_DB_PASSWORD", "");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_PORT = 8082;
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;

    // ==================== 数据库初始化 ====================

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2驱动加载失败，请确保h2.jar在classpath中", e);
        }
    }

    static Connection getConn() throws SQLException {
        String configuredPath = System.getProperty("blog.db.path",
                System.getenv().getOrDefault("BLOG_DB_PATH", "data/blogdb"));
        Path databasePath = Path.of(configuredPath).toAbsolutePath().normalize();
        try {
            Path parent = databasePath.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SQLException("无法创建数据库目录", e);
        }
        String path = databasePath.toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + path + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }

    static void initDB() {
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            // 1. 文章表
            stmt.execute("CREATE TABLE IF NOT EXISTS articles (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(200) NOT NULL, " +
                    "content TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            // 2. 标签表
            stmt.execute("CREATE TABLE IF NOT EXISTS tags (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL UNIQUE)");
            // 3. 多对多关联表
            stmt.execute("CREATE TABLE IF NOT EXISTS article_tags (" +
                    "article_id INT NOT NULL, " +
                    "tag_id INT NOT NULL, " +
                    "PRIMARY KEY (article_id, tag_id), " +
                    "FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE)");

            // 预置标签
            String[] preset = {"Java", "Python", "数据库", "前端", "后端",
                    "算法", "设计模式", "Linux", "Spring", "Vue"};
            for (String name : preset) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "MERGE INTO tags (name) KEY(name) VALUES (?)")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
            }

            // 预置几篇示例文章（如果表为空）
            boolean needsSeedData;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM articles")) {
                rs.next();
                needsSeedData = rs.getInt(1) == 0;
            }
            if (needsSeedData) {
                seedData(conn);
            }
            System.out.println("数据库初始化完成（H2嵌入式）");
        } catch (SQLException e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    /** 预置示例数据 */
    static void seedData(Connection conn) throws SQLException {
        // 查标签ID
        Map<String, Integer> tagMap = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM tags")) {
            while (rs.next()) tagMap.put(rs.getString("name"), rs.getInt("id"));
        }

        // 添加文章，每篇关联2-3个标签
        addArticle(conn, "Spring Boot快速入门",
                "Spring Boot是由Pivotal团队提供的全新框架，用于简化Spring应用的初始搭建和开发过程。",
                -1, tagMap, "Java", "Spring", "设计模式");
        addArticle(conn, "MySQL索引优化实战",
                "索引是帮助MySQL高效获取数据的数据结构，常见的索引类型包括B+树索引、哈希索引等。",
                -2, tagMap, "数据库", "设计模式");
        addArticle(conn, "Vue3组合式API详解",
                "Composition API是Vue3最大的变革之一，提供了更灵活的代码组织方式。",
                -3, tagMap, "前端", "Vue");
        addArticle(conn, "Java多线程编程指南",
                "Java提供了丰富的多线程支持，从Thread类到Executor框架全面解析。",
                -4, tagMap, "Java", "算法");
        addArticle(conn, "Linux服务器部署实战",
                "掌握Linux命令行是后端开发必备技能，本文整理了常用命令和部署流程。",
                -5, tagMap, "Linux", "后端");
        addArticle(conn, "Python数据分析入门",
                "Python在数据分析领域应用广泛，本文介绍Pandas和NumPy的基本用法。",
                -6, tagMap, "Python", "算法");
        addArticle(conn, "Docker容器化实践",
                "Docker是一个开源的应用容器引擎，可以将应用打包到容器中运行。",
                -7, tagMap, "Linux", "后端", "设计模式");
    }

    static void addArticle(Connection conn, String title, String content,
                           int daysAgo, Map<String, Integer> tagMap, String... tagNames)
            throws SQLException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysAgo);
        Timestamp ts = new Timestamp(cal.getTimeInMillis());

        int artId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO articles (title, content, created_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setTimestamp(3, ts);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("文章主键未返回");
                artId = keys.getInt(1);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO article_tags (article_id, tag_id) VALUES (?, ?)")) {
            for (String tn : tagNames) {
                Integer tid = tagMap.get(tn);
                if (tid != null) {
                    ps.setInt(1, artId);
                    ps.setInt(2, tid);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    // ==================== 主入口 ====================

    public static void main(String[] args) throws Exception {
        int port = parsePort(args);
        initDB();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", new ListPage());       // 文章列表+搜索+分页
        server.createContext("/detail", new DetailPage());// 文章详情
        server.createContext("/create", new CreatePage());// 发布文章页面
        server.createContext("/save", new SaveArticle()); // 处理发布
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));
        server.setExecutor(executor);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));
        System.out.println("博客系统启动成功！浏览器访问: http://localhost:" + port);
    }

    // ==================== 文章列表页（组合搜索 + 分页） ====================

    static class ListPage implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"GET".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 GET 请求");
                return;
            }
            String q = t.getRequestURI().getQuery();
            Map<String, String> p = parseQuery(q);

            String keyword = p.getOrDefault("keyword", "").trim();
            String tagName = p.getOrDefault("tagName", "").trim();
            String dateFrom = p.getOrDefault("dateFrom", "").trim();
            String dateTo = p.getOrDefault("dateTo", "").trim();
            int requestedPage = Math.max(1, toInt(p.get("page"), 1));
            int pageSize = 4;

            if (keyword.length() > 100 || tagName.length() > 50) {
                sendHtml(t, 400, "搜索条件过长");
                return;
            }
            LocalDate fromDate;
            LocalDate throughDate;
            try {
                fromDate = dateFrom.isEmpty() ? null : LocalDate.parse(dateFrom);
                throughDate = dateTo.isEmpty() ? null : LocalDate.parse(dateTo);
            } catch (java.time.DateTimeException e) {
                sendHtml(t, 400, "日期格式无效，请使用 YYYY-MM-DD");
                return;
            }
            if (fromDate != null && throughDate != null && fromDate.isAfter(throughDate)) {
                sendHtml(t, 400, "开始日期不能晚于结束日期");
                return;
            }

            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> parameters = new ArrayList<>();
            if (!keyword.isEmpty()) {
                where.append(" AND LOWER(a.title) LIKE LOWER(?)");
                parameters.add("%" + keyword + "%");
            }
            if (!tagName.isEmpty()) {
                where.append(" AND t.name = ?");
                parameters.add(tagName);
            }
            if (fromDate != null) {
                where.append(" AND a.created_at >= ?");
                parameters.add(Timestamp.valueOf(fromDate.atStartOfDay()));
            }
            if (throughDate != null) {
                where.append(" AND a.created_at < ?");
                parameters.add(Timestamp.valueOf(throughDate.plusDays(1).atStartOfDay()));
            }

            int total = 0;
            try (Connection conn = getConn();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(DISTINCT a.id) FROM articles a " +
                                 "LEFT JOIN article_tags at ON a.id = at.article_id " +
                                 "LEFT JOIN tags t ON at.tag_id = t.id" + where)) {
                bindParameters(stmt, parameters);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) total = rs.getInt(1);
            } catch (SQLException e) {
                System.err.println("文章计数查询失败：" + e.getMessage());
                sendHtml(t, 500, "暂时无法读取文章列表");
                return;
            }

            int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
            int page = Math.min(requestedPage, totalPages);
            long offset = (long) (page - 1) * pageSize;

            // 查询当前页数据
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = getConn();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT DISTINCT a.* FROM articles a " +
                                 "LEFT JOIN article_tags at ON a.id = at.article_id " +
                                 "LEFT JOIN tags t ON at.tag_id = t.id" + where +
                                 " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?");
                 PreparedStatement tagStmt = conn.prepareStatement(
                         "SELECT t.name FROM tags t " +
                                 "INNER JOIN article_tags at ON t.id = at.tag_id " +
                                 "WHERE at.article_id = ? ORDER BY t.name")) {
                bindParameters(stmt, parameters);
                stmt.setInt(parameters.size() + 1, pageSize);
                stmt.setLong(parameters.size() + 2, offset);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("title", rs.getString("title"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    List<String> tags = new ArrayList<>();
                    tagStmt.setInt(1, rs.getInt("id"));
                    try (ResultSet rs2 = tagStmt.executeQuery()) {
                        while (rs2.next()) tags.add(rs2.getString("name"));
                    }
                    row.put("tags", tags);
                    rows.add(row);
                }
            } catch (SQLException e) {
                System.err.println("文章列表查询失败：" + e.getMessage());
                sendHtml(t, 500, "暂时无法读取文章列表");
                return;
            }

            // 渲染HTML
            StringBuilder h = new StringBuilder();
            h.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            h.append("<title>博客系统</title>");
            h.append("<style>");
            h.append("body{font-family:'Segoe UI','Microsoft YaHei',sans-serif;background:#f0f2f5;min-height:100vh;margin:0;padding:0;}");
            h.append(".navbar{background:white;padding:0 24px;height:56px;display:flex;align-items:center;");
            h.append("justify-content:space-between;box-shadow:0 2px 8px rgba(0,0,0,0.08);margin-bottom:24px;}");
            h.append(".navbar .brand{font-weight:700;font-size:18px;color:#8b5cf6;}");
            h.append(".navbar a{background:linear-gradient(135deg,#8b5cf6,#6366f1);color:white;");
            h.append("padding:8px 18px;border-radius:8px;text-decoration:none;font-size:14px;font-weight:500;transition:transform 0.2s;}");
            h.append(".navbar a:hover{transform:translateY(-1px);}");
            h.append(".container{max-width:860px;margin:0 auto;padding:0 20px;}");
            h.append(".search-card{background:white;border-radius:16px;padding:20px 24px;");
            h.append("box-shadow:0 2px 12px rgba(0,0,0,0.06);margin-bottom:20px;}");
            h.append(".search-card input,.search-card select{padding:8px 12px;border:2px solid #e2e8f0;border-radius:8px;margin:4px;font-size:14px;outline:none;}");
            h.append(".search-card input:focus,.search-card select:focus{border-color:#8b5cf6;}");
            h.append(".search-card button{background:linear-gradient(135deg,#8b5cf6,#6366f1);color:white;");
            h.append("border:none;padding:8px 20px;border-radius:8px;cursor:pointer;font-weight:500;transition:transform 0.2s;}");
            h.append(".search-card button:hover{transform:translateY(-1px);}");
            h.append(".search-card .clear{color:#94a3b8;text-decoration:none;margin-left:8px;font-size:14px;}");
            h.append(".stat{color:#64748b;font-size:14px;margin-bottom:12px;}");
            h.append(".article-card{background:white;border-radius:12px;padding:18px 22px;");
            h.append("box-shadow:0 2px 8px rgba(0,0,0,0.04);margin-bottom:12px;transition:transform 0.2s,box-shadow 0.2s;}");
            h.append(".article-card:hover{transform:translateY(-2px);box-shadow:0 6px 24px rgba(0,0,0,0.08);}");
            h.append(".article-card h3{margin:0 0 8px 0;font-size:18px;}");
            h.append(".article-card h3 a{color:#1e293b;text-decoration:none;}");
            h.append(".article-card h3 a:hover{color:#8b5cf6;}");
            h.append(".article-card .meta{color:#94a3b8;font-size:13px;margin-bottom:8px;}");
            h.append(".tag{display:inline-block;background:#ede9fe;color:#6d28d9;padding:3px 10px;border-radius:20px;font-size:12px;margin:2px;font-weight:500;}");
            h.append(".page-nav{text-align:center;margin-top:24px;display:flex;justify-content:center;align-items:center;gap:8px;}");
            h.append(".page-nav a{display:inline-block;padding:8px 18px;background:white;color:#8b5cf6;text-decoration:none;border-radius:8px;font-weight:500;box-shadow:0 2px 8px rgba(0,0,0,0.04);}");
            h.append(".page-nav a:hover{background:#8b5cf6;color:white;}");
            h.append(".page-nav span{color:#64748b;padding:8px 16px;font-size:14px;}");
            h.append(".empty-state{text-align:center;padding:60px;color:#94a3b8;}");
            h.append(".empty-state .icon{font-size:56px;margin-bottom:12px;}");
            h.append("</style></head><body>");
            h.append("<div class='navbar'><span class='brand'>📝 博客文章管理</span>");
            h.append("<a href='/create'>+ 发布新文章</a></div>");
            h.append("<div class='container'>");

            // 搜索表单
            h.append("<div class='search-card'><form method='get' action='/'>");
            h.append("🔍 标题关键字：<input name='keyword' value='").append(esc(keyword)).append("' placeholder='搜索标题...' size='14'> ");
            h.append("🏷 标签：<select name='tagName'><option value=''>全部</option>");
            // 读标签列表
            try (Connection conn = getConn(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM tags ORDER BY id")) {
                while (rs.next()) {
                    String sel = rs.getString("name").equals(tagName) ? " selected" : "";
                    h.append("<option value='").append(esc(rs.getString("name"))).append("'")
                            .append(sel).append(">").append(esc(rs.getString("name"))).append("</option>");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            h.append("</select>&nbsp;");
            h.append("📅 时间：<input type='date' name='dateFrom' value='").append(esc(dateFrom)).append("'> 至 ");
            h.append("<input type='date' name='dateTo' value='").append(esc(dateTo)).append("'> ");
            h.append("<button type='submit'>搜索</button>");
            h.append("<a class='clear' href='/'>清除条件</a>");
            h.append("</form></div>");

            h.append("<div class='stat'>📄 共 <b>").append(total).append("</b> 篇文章</div>");

            if (rows.isEmpty()) {
                h.append("<div class='empty-state'><div class='icon'>📭</div>没有找到匹配的文章</div>");
            } else {
                for (Map<String, Object> row : rows) {
                    h.append("<div class='article-card'>");
                    h.append("<h3><a href='/detail?id=").append(row.get("id")).append("'>")
                            .append(esc((String) row.get("title"))).append("</a></h3>");
                    h.append("<div class='meta'>🕐 ")
                            .append(formatDateTime((Timestamp) row.get("createdAt"))).append("</div>");
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) row.get("tags");
                    for (String tn : tags) {
                        h.append("<span class='tag'>").append(esc(tn)).append("</span>");
                    }
                    h.append("</div>");
                }
            }

            // 分页
            if (totalPages > 1) {
                h.append("<div class='page-nav'>");
                String base = "";
                if (!keyword.isEmpty()) base += "&keyword=" + urlEnc(keyword);
                if (!tagName.isEmpty()) base += "&tagName=" + urlEnc(tagName);
                if (!dateFrom.isEmpty()) base += "&dateFrom=" + urlEnc(dateFrom);
                if (!dateTo.isEmpty()) base += "&dateTo=" + urlEnc(dateTo);
                if (page > 1)
                    h.append("<a href='/?page=").append(page - 1).append(base).append("'>← 上一页</a>");
                h.append("<span>").append(page).append(" / ").append(totalPages).append(" 页</span>");
                if (page < totalPages)
                    h.append("<a href='/?page=").append(page + 1).append(base).append("'>下一页 →</a>");
                h.append("</div>");
            }

            h.append("</div></body></html>");
            sendHtml(t, h.toString());
        }
    }

    // ==================== 文章详情页 ====================

    static class DetailPage implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"GET".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 GET 请求");
                return;
            }
            int id = toInt(getParam(t.getRequestURI().getQuery(), "id"), 0);

            StringBuilder h = new StringBuilder();
            h.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            h.append("<title>文章详情</title>");
            h.append("<style>");
            h.append("body{font-family:'Segoe UI','Microsoft YaHei',sans-serif;background:#f0f2f5;min-height:100vh;margin:0;padding:0;}");
            h.append(".container{max-width:780px;margin:0 auto;padding:24px 20px;}");
            h.append(".article-full{background:white;border-radius:16px;padding:32px;box-shadow:0 2px 12px rgba(0,0,0,0.06);}");
            h.append(".article-full h1{color:#1e293b;margin:0 0 16px 0;font-size:26px;}");
            h.append(".article-full .meta{color:#94a3b8;font-size:14px;margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid #f1f5f9;}");
            h.append(".tags-row{margin-bottom:20px;}");
            h.append(".tag{display:inline-block;background:#ede9fe;color:#6d28d9;padding:4px 12px;border-radius:20px;font-size:13px;margin:3px;font-weight:500;}");
            h.append(".content{line-height:2;font-size:15px;color:#334155;white-space:pre-wrap;}");
            h.append(".btn-back{display:inline-block;margin-top:24px;padding:10px 24px;");
            h.append("background:linear-gradient(135deg,#8b5cf6,#6366f1);color:white;text-decoration:none;border-radius:10px;font-weight:500;transition:transform 0.2s;}");
            h.append(".btn-back:hover{transform:translateY(-1px);}");
            h.append(".notfound{text-align:center;padding:80px;color:#94a3b8;font-size:18px;}");
            h.append("</style></head><body><div class='container'>");

            try (Connection conn = getConn();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM articles WHERE id=?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String title = rs.getString("title");
                    String content = rs.getString("content");
                    Timestamp ts = rs.getTimestamp("created_at");

                    h.append("<div class='article-full'>");
                    h.append("<h1>").append(esc(title)).append("</h1>");
                    h.append("<div class='meta'>🕐 发布时间：").append(formatDateTime(ts)).append("</div>");

                    // 一次性获取所有关联标签
                    h.append("<div class='tags-row'>");
                    PreparedStatement ps2 = conn.prepareStatement(
                            "SELECT t.name FROM tags t " +
                                    "INNER JOIN article_tags at ON t.id = at.tag_id " +
                                    "WHERE at.article_id = ? ORDER BY t.name");
                    ps2.setInt(1, id);
                    ResultSet rs2 = ps2.executeQuery();
                    while (rs2.next()) {
                        h.append("<span class='tag'>🏷 ").append(esc(rs2.getString("name"))).append("</span>");
                    }
                    rs2.close();
                    ps2.close();
                    h.append("</div>");

                    h.append("<div class='content'>").append(esc(content)).append("</div>");
                    h.append("</div>");
                } else {
                    h.append("<div class='notfound'>📭 文章不存在</div>");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            h.append("<a class='btn-back' href='/'>← 返回文章列表</a>");
            h.append("</div></body></html>");
            sendHtml(t, h.toString());
        }
    }

    // ==================== 发布文章页面 ====================

    static class CreatePage implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"GET".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 GET 请求");
                return;
            }
            String error = getParam(t.getRequestURI().getQuery(), "error");
            StringBuilder h = new StringBuilder();
            h.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            h.append("<title>发布新文章</title>");
            h.append("<style>");
            h.append("body{font-family:'Segoe UI','Microsoft YaHei',sans-serif;background:#f0f2f5;min-height:100vh;margin:0;padding:0;}");
            h.append(".container{max-width:660px;margin:0 auto;padding:24px 20px;}");
            h.append(".card{background:white;border-radius:16px;padding:32px;box-shadow:0 2px 12px rgba(0,0,0,0.06);}");
            h.append(".card h2{margin:0 0 24px 0;color:#1e293b;font-size:22px;text-align:center;}");
            h.append("label{display:block;margin-top:16px;font-weight:600;color:#475569;font-size:14px;}");
            h.append("input[type=text],textarea{width:100%;padding:10px 14px;margin-top:6px;");
            h.append("border:2px solid #e2e8f0;border-radius:10px;font-size:14px;outline:none;font-family:inherit;transition:border 0.3s;}");
            h.append("input[type=text]:focus,textarea:focus{border-color:#8b5cf6;}");
            h.append("textarea{height:200px;resize:vertical;}");
            h.append(".tag-grid{margin-top:8px;display:flex;flex-wrap:wrap;gap:6px;}");
            h.append(".tag-grid label{display:flex;align-items:center;gap:4px;margin:0;");
            h.append("background:#f8fafc;padding:6px 14px;border-radius:20px;cursor:pointer;");
            h.append("border:2px solid transparent;transition:all 0.2s;font-weight:normal;font-size:14px;}");
            h.append(".tag-grid label:has(input:checked){border-color:#8b5cf6;background:#ede9fe;color:#6d28d9;}");
            h.append(".tag-grid input[type=checkbox]{accent-color:#8b5cf6;}");
            h.append("button{margin-top:24px;width:100%;padding:12px;");
            h.append("background:linear-gradient(135deg,#8b5cf6,#6366f1);color:white;border:none;");
            h.append("border-radius:10px;font-size:16px;font-weight:600;cursor:pointer;transition:transform 0.2s,box-shadow 0.2s;}");
            h.append("button:hover{transform:translateY(-1px);box-shadow:0 8px 25px rgba(99,102,241,0.4);}");
            h.append(".btn-back{display:block;text-align:center;margin-top:16px;color:#94a3b8;text-decoration:none;font-size:14px;}");
            h.append(".btn-back:hover{color:#6366f1;}");
            h.append("</style></head><body><div class='container'><div class='card'>");
            h.append("<h2>✍️ 发布新文章</h2>");
            if (error != null && !error.isBlank()) {
                h.append("<p style='color:#b91c1c;margin-bottom:16px;'>")
                        .append(esc(error)).append("</p>");
            }

            h.append("<form method='post' action='/save'>");
            h.append("<label>📌 文章标题</label>");
            h.append("<input type='text' name='title' required placeholder='请输入文章标题'>");
            h.append("<label>📝 文章内容</label>");
            h.append("<textarea name='content' required placeholder='请输入文章内容'></textarea>");
            h.append("<label>🏷 选择标签（可多选）</label>");
            h.append("<div class='tag-grid'>");

            // 从数据库读取标签
            try (Connection conn = getConn();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name FROM tags ORDER BY id")) {
                while (rs.next()) {
                    h.append("<label><input type='checkbox' name='tag' value='")
                            .append(rs.getInt("id")).append("'> ")
                            .append(esc(rs.getString("name"))).append("</label>");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            h.append("</div>");
            h.append("<button type='submit'>发布文章</button>");
            h.append("</form>");

            h.append("</div><a class='btn-back' href='/'>← 返回文章列表</a></div>");
            h.append("</div></body></html>");
            sendHtml(t, h.toString());
        }
    }

    // ==================== 保存文章 ====================

    static class SaveArticle implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equalsIgnoreCase(t.getRequestMethod())) {
                sendHtml(t, 405, "仅支持 POST 请求");
                return;
            }
            byte[] bodyBytes;
            try {
                bodyBytes = readLimited(t.getRequestBody(), MAX_REQUEST_BYTES);
            } catch (IOException e) {
                sendHtml(t, 413, "文章内容超过限制");
                return;
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String title = getParam(body, "title");
            String content = getParam(body, "content");
            title = title == null ? "" : title.trim();
            content = content == null ? "" : content.trim();

            if (title.isEmpty() || content.isEmpty()) {
                redirect(t, "/create?error=" + urlEnc("标题和内容不能为空！"));
                return;
            }
            if (title.length() > 200 || content.length() > 100_000) {
                redirect(t, "/create?error=" + urlEnc("标题最多200个字符，正文最多100000个字符。"));
                return;
            }

            Set<Integer> tagIds = new LinkedHashSet<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    try {
                        if ("tag".equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8))) {
                            tagIds.add(Integer.parseInt(URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
                        }
                    } catch (IllegalArgumentException e) {
                        redirect(t, "/create?error=" + urlEnc("所选标签无效。"));
                        return;
                    }
                }
            }

            try (Connection conn = getConn()) {
                conn.setAutoCommit(false);
                try {
                    int articleId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO articles (title, content) VALUES (?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, title);
                        ps.setString(2, content);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("文章主键未返回");
                            }
                            articleId = keys.getInt(1);
                        }
                    }

                    if (!tagIds.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO article_tags (article_id, tag_id) VALUES (?, ?)")) {
                            for (int tagId : tagIds) {
                                ps.setInt(1, articleId);
                                ps.setInt(2, tagId);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                System.err.println("文章保存失败：" + e.getMessage());
                redirect(t, "/create?error=" + urlEnc("保存失败，请检查所选标签后重试。"));
                return;
            }

            // 发布成功，跳转列表页
            redirect(t, "/");
        }
    }

    // ==================== 工具方法 ====================

    private static Map<String, String> parseQuery(String str) {
        Map<String, String> map = new LinkedHashMap<>();
        if (str == null || str.isEmpty()) return map;
        for (String pair : str.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) {
                    // 忽略格式错误的参数，不让单个输入中断页面处理。
                }
            }
        }
        return map;
    }

    private static String getParam(String data, String key) {
        return parseQuery(data).get(key);
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatDateTime(Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(DATE_TIME);
    }

    private static void bindParameters(PreparedStatement statement, List<Object> parameters)
            throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private static int parsePort(String[] args) {
        String configuredPort = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault("PORT", Integer.toString(DEFAULT_PORT));
        int port = Integer.parseInt(configuredPort);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1 到 65535 之间");
        }
        return port;
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        byte[] body = input.readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw new IOException("请求体过大");
        }
        return body;
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String urlEnc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void redirect(HttpExchange t, String path) throws IOException {
        t.getResponseHeaders().set("Location", path);
        t.sendResponseHeaders(302, -1);
        t.close();
    }

    private static void sendHtml(HttpExchange t, String html) throws IOException {
        sendHtml(t, 200, html);
    }

    private static void sendHtml(HttpExchange t, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        t.getResponseHeaders().set("Cache-Control", "no-store");
        t.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = t.getResponseBody()) {
            output.write(bytes);
        }
    }
}

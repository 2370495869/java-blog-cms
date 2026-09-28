package com.blogcms.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class MarkdownRendererTest {
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void escapesRawHtmlAndRemovesEventHandlers() {
        String html = renderer.render("<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>");

        assertThat(Jsoup.parseBodyFragment(html).select("script")).isEmpty();
        assertThat(Jsoup.parseBodyFragment(html).select("[onerror]")).isEmpty();
    }

    @Test
    void stripsDangerousLinkProtocols() {
        String html = renderer.render("[open](javascript:alert%281%29)");

        assertThat(Jsoup.parseBodyFragment(html).select("a[href^=javascript]")).isEmpty();
        assertThat(html.toLowerCase()).doesNotContain("javascript:");
    }

    @Test
    void supportsMarkdownTablesAndSafeLinks() {
        String html = renderer.render("| A | B |\n| --- | --- |\n| one | [site](https://example.com) |");
        var document = Jsoup.parseBodyFragment(html);

        assertThat(document.select("table td")).hasSize(2);
        assertThat(document.selectFirst("a[href=https://example.com]")).isNotNull();
        assertThat(document.selectFirst("a").attr("rel")).contains("nofollow", "noopener", "noreferrer");
    }
}

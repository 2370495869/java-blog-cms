package com.blogcms.service;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component("markdownRenderer")
public class MarkdownRenderer {
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();
        safelist = Safelist.basic()
                .addTags("table", "thead", "tbody", "tr", "th", "td")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");
    }

    public String render(String markdown) {
        String html = renderer.render(parser.parse(markdown == null ? "" : markdown));
        return Jsoup.clean(html, "", safelist,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }
}

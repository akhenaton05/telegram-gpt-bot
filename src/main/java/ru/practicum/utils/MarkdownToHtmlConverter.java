package ru.practicum.utils;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.misc.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownToHtmlConverter {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownToHtmlConverter() {
        // Включаем поддержку таблиц
        List<Extension> exts = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(exts).build();
        this.renderer = HtmlRenderer.builder().extensions(exts).build();
    }

    // Конвертация Markdown → Telegram HTML
    public String convertMarkdownToTelegramHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        // Рендерим Markdown в HTML (Flexmark с таблицами)
        String html = renderer.render(parser.parse(markdown));
        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();

        // Конвертируем DOM в Telegram HTML
        return renderTelegramHtml(body).trim();
    }

    // Рекурсивный рендер DOM дерева в Telegram HTML
    private String renderTelegramHtml(Node node) {
        StringBuilder sb = new StringBuilder();

        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                sb.append(escapeHtml(textNode.text()));
            } else if (child instanceof Element el) {
                String tag = el.tagName();

                switch (tag) {
                    case "h1": case "h2": case "h3":
                    case "h4": case "h5": case "h6":
                        sb.append("<b>").append(renderTelegramHtml(el)).append("</b>\n\n");
                        break;

                    case "p":
                        sb.append(renderTelegramHtml(el)).append("\n\n");
                        break;

                    case "strong": case "b":
                        sb.append("<b>").append(renderTelegramHtml(el)).append("</b>");
                        break;

                    case "em": case "i":
                        sb.append("<i>").append(renderTelegramHtml(el)).append("</i>");
                        break;

                    case "code":
                        // inline <code>
                        sb.append("<code>").append(escapeHtml(el.text())).append("</code>");
                        break;

                    case "pre":
                        // блок кода
                        sb.append("<pre><code>")
                                .append(escapeHtml(el.text()))
                                .append("</code></pre>\n\n");
                        break;

                    case "ul":
                        for (Element li : el.select("> li")) {
                            sb.append("• ").append(renderTelegramHtml(li)).append("\n");
                        }
                        sb.append("\n");
                        break;

                    case "ol":
                        int idx = 1;
                        for (Element li : el.select("> li")) {
                            sb.append(idx++).append(". ").append(renderTelegramHtml(li)).append("\n");
                        }
                        sb.append("\n");
                        break;

                    case "li":
                        sb.append(renderTelegramHtml(el));
                        break;

                    case "a":
                        String href = el.attr("href");
                        sb.append("<a href=\"").append(escapeHtmlAttr(href)).append("\">")
                                .append(renderTelegramHtml(el))
                                .append("</a>");
                        break;

                    case "br":
                        sb.append("\n");
                        break;

                    case "table":
                        // преобразуем таблицу в выровненный моноширинный текст и оборачиваем в <pre>
                        sb.append("<pre>").append(escapeHtml(parseTableToMonospace(el))).append("</pre>\n\n");
                        break;

                    default:
                        sb.append(renderTelegramHtml(el));
                }
            }
        }

        return sb.toString();
    }

    // Берёт HTML-таблицу (Jsoup Element) и возвращает строку: выровненный моноширинный текст (строки + ' | ' разделитель).
    private String parseTableToMonospace(Element table) {
        List<List<String>> rows = new ArrayList<>();
        List<Boolean> headerFlags = new ArrayList<>();
        int columnCount = 0;

        for (Element row : table.select("tr")) {
            List<String> cols = new ArrayList<>();
            boolean isHeader = false;
            for (Element cell : row.select("th, td")) {
                String txt = cell.text().trim();
                cols.add(txt);
                if ("th".equals(cell.tagName())) isHeader = true;
            }
            columnCount = Math.max(columnCount, cols.size());
            if (!cols.isEmpty()) {
                rows.add(cols);
                headerFlags.add(isHeader);
            }
        }

        // вычисляем максимальную ширину по колонкам
        int[] widths = new int[columnCount];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < columnCount; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                sb.append(padRight(cell, widths[c]));
                if (c < columnCount - 1) sb.append(" | ");
            }
            sb.append("\n");

            // если это заголовок — добавим разделитель строкой дефисов
            if (headerFlags.get(r)) {
                for (int c = 0; c < columnCount; c++) {
                    sb.append(repeatChar('-', widths[c]));
                    if (c < columnCount - 1) sb.append("-+-");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private String repeatChar(char ch, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    // Экранирование текста для HTML (для безопасной вставки в <pre> / <code> / <a>)
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeHtmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

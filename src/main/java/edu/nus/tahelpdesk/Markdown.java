package edu.nus.tahelpdesk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Escapes all supplied HTML before a deliberately small Markdown allowlist is rendered. */
public final class Markdown {
  private Markdown() {}
  private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\((https?://[^ )]+)\\)");
  public static String render(String source) {
    String escaped = source == null ? "" : source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    Matcher links = LINK.matcher(escaped); StringBuffer out = new StringBuffer();
    while (links.find()) links.appendReplacement(out, "<a href=\"" + links.group(2) + "\" rel=\"noopener noreferrer\">" + links.group(1) + "</a>");
    links.appendTail(out);
    return out.toString().replaceAll("`([^`]+)`", "<code>$1</code>")
        .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
        .replaceAll("(?<!\\*)\\*([^*]+)\\*", "<em>$1</em>")
        .replace("\n", "<br>\n");
  }
}

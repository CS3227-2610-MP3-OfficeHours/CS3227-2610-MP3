package edu.nus.tahelpdesk;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.text.PDFTextStripper;

/** Bounded, non-executing PDFBox adapter. PDF text remains untrusted input. */
final class PdfPageExtractor {
  static final String PARSER_VERSION = "pdfbox-3.0.8";
  static final int MAX_PAGES = 100;
  static final int MAX_TEXT_PER_PAGE = 24_000;
  static final int MAX_TOTAL_TEXT = 120_000;
  /** A single parser worker bounds concurrent PDF parsing. */
  private static final ExecutorService PARSER = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "pdfbox-parser"); thread.setDaemon(true); return thread; });

  record Result(int pageCount, List<String> pages, String failureCode) {
    boolean succeeded() { return failureCode == null; }
    boolean hasText() { return pages.stream().anyMatch(page -> !page.isBlank()); }
  }

  private PdfPageExtractor() {}

  static Result inspect(byte[] content) { return boundedExtract(content, false); }
  static Result extract(byte[] content) { return boundedExtract(content, true); }

  private static Result boundedExtract(byte[] content, boolean extractText) {
    Future<Result> parse = PARSER.submit(() -> extract(content, extractText));
    try { return parse.get(); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); parse.cancel(true); return failed("malformed"); }
    catch (Exception e) { return failed("malformed"); }
  }
  private static Result extract(byte[] content, boolean extractText) {
    try (RandomAccessReadBuffer source = new RandomAccessReadBuffer(content);
         PDDocument document = new PDFParser(source).parse(false)) {
      if (document.isEncrypted()) return failed("encrypted");
      int pageCount = document.getNumberOfPages();
      if (pageCount < 1) return failed("malformed");
      if (pageCount > MAX_PAGES) return failed("page_limit");
      if (!extractText) return new Result(pageCount, List.of(), null);
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(false);
      List<String> pages = new ArrayList<>(pageCount);
      int total = 0;
      for (int page = 1; page <= pageCount; page++) {
        stripper.setStartPage(page); stripper.setEndPage(page);
        String text = normalize(stripper.getText(document));
        if (text.length() > MAX_TEXT_PER_PAGE) return failed("text_limit");
        total += text.length();
        if (total > MAX_TOTAL_TEXT) return failed("text_limit");
        pages.add(text);
      }
      return new Result(pageCount, List.copyOf(pages), null);
    } catch (IOException | RuntimeException e) {
      return failed("malformed");
    }
  }

  static String digest(byte[] value) { return digestBytes(value); }
  static String digestText(String value) { return digestBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  private static String digestBytes(byte[] value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable.", e); }
  }
  private static Result failed(String code) { return new Result(0, List.of(), code); }
  private static String normalize(String value) {
    return value.replace('\u0000', ' ').replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
        .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\\n\\n").trim();
  }
}

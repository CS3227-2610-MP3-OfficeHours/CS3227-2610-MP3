package edu.nus.tahelpdesk;

import edu.nus.tahelpdesk.StudentAnswerPolicy.Scope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Student AI help: scope resolution, map-narrowed retrieval over original PDF
 * pages, and deterministic citation validation.
 *
 * <p>The caller is responsible for authorization and for supplying only
 * eligible, same-course material. This class never reads storage, so it cannot
 * widen the course boundary the caller established.
 *
 * <p>Approved Knowledge Map text is used for scoring only. It is never sent to
 * the gateway and never returned, so map content cannot carry instructions
 * into a model call or reach a student.
 */
final class StudentAiService {
  static final int MAX_QUESTION_CHARS = 1_000;
  private static final int MAX_PAGES = 8;
  private static final int MAX_MATERIALS = 3;
  private static final int MAX_PACKET_CHARS = 24_000;
  private static final int MAX_TERM_HITS = 3;

  /** One extracted, text-bearing page of an active course PDF. */
  record SourcePage(UUID materialId, String filename, int pageNumber, String text) {}

  /** An eligible active PDF, with its approved and source-current map text when one exists. */
  record Candidate(UUID materialId, String filename, String approvedMapText, List<SourcePage> pages) {}

  record Citation(UUID materialId, String filename, int page) {}

  /** Outcome vocabulary matching the approved API contract. */
  enum Outcome { administrative, guidance, refused, insufficient_evidence }

  record Answer(Outcome outcome, String text, List<Citation> citations) {}

  /** Gateway access, injected so tests exercise the policy without a provider. */
  interface Provider {
    SoCLaaSClient.Result classify(String systemPrompt, String question);
    SoCLaaSClient.Result compose(String systemPrompt, String userPrompt);
  }

  /** Raised for the states the API reports instead of an answer. */
  static final class Unanswerable extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final ApiException api;
    Unanswerable(ApiException api) { super(api.getMessage(), null, false, false); this.api = api; }
  }

  private static final Pattern CITATION =
      Pattern.compile("\\(\\s*([^()\\r\\n]{1,200}?\\.pdf)\\s*,\\s*p\\.\\s*(\\d{1,4})\\s*\\)", Pattern.CASE_INSENSITIVE);
  /** Any PDF reference left after the canonical citations are masked out. */
  private static final Pattern ANY_PDF_MENTION =
      Pattern.compile("\\S{0,200}\\.pdf", Pattern.CASE_INSENSITIVE);
  private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
  /** Stands in for a validated citation while sentence structure is checked. */
  private static final String CITED = "\u0001";

  private StudentAiService() {}

  /**
   * Resolves the scope, retrieves bounded page evidence, composes one answer,
   * and validates its citations before returning it.
   */
  static Answer answer(String question, List<Candidate> candidates, Provider provider) {
    Scope screened = StudentAnswerPolicy.screen(question);
    Scope scope = screened == Scope.refused ? Scope.refused : classify(question, screened, provider);

    if (scope == Scope.refused) return new Answer(Outcome.refused, StudentAnswerPolicy.refusalFallback(), List.of());
    if (candidates.isEmpty()) throw new Unanswerable(ApiException.conflict(
        "This course has no readable PDFs yet, so there is nothing to answer from. Ask on the forum instead."));

    List<SourcePage> selected = select(question, candidates);
    if (selected.isEmpty()) return insufficient();

    SoCLaaSClient.Result result = provider.compose(
        StudentAnswerPolicy.composerSystemPrompt(scope), userPrompt(question, selected));
    if (!result.succeeded()) throw new Unanswerable(providerFailure(result.failure()));
    if (StudentAnswerPolicy.leaksPolicy(result.text())) return insufficient();

    List<Citation> citations = validate(result.text(), selected);
    if (citations.isEmpty()) return insufficient();
    Outcome outcome = scope == Scope.administrative ? Outcome.administrative : Outcome.guidance;
    return new Answer(outcome, result.text(), citations);
  }

  /**
   * The classifier sees only the student's question. A failure or an
   * unrecognised label yields {@code academic}, never a wider scope.
   */
  private static Scope classify(String question, Scope screened, Provider provider) {
    SoCLaaSClient.Result label = provider.classify(StudentAnswerPolicy.classifierSystemPrompt(), question);
    if (!label.succeeded() && label.failure() == SoCLaaSClient.Failure.rate_limited)
      throw new Unanswerable(providerFailure(label.failure()));
    Scope advisory = label.succeeded() ? StudentAnswerPolicy.parseLabel(label.text()) : Scope.academic;
    return StudentAnswerPolicy.resolve(screened, advisory);
  }

  private static Answer insufficient() {
    return new Answer(Outcome.insufficient_evidence, StudentAnswerPolicy.insufficientEvidence(), List.of());
  }

  /**
   * Scores every eligible page by question-term overlap, boosted by the
   * material's approved Knowledge Map, then applies the page, material, and
   * character caps. A material with no approved current map is still eligible;
   * the map only changes ordering.
   */
  private static List<SourcePage> select(String question, List<Candidate> candidates) {
    List<String> terms = StudentAnswerPolicy.queryTerms(question);
    if (terms.isEmpty()) return List.of();
    record Scored(SourcePage page, double score) {}
    List<Scored> scored = new ArrayList<>();
    for (Candidate candidate : candidates) {
      double boost = 1.0 + 0.4 * Math.min(5, termHits(candidate.approvedMapText(), terms));
      for (SourcePage page : candidate.pages()) {
        double score = termHits(page.text(), terms) * boost;
        if (score > 0) scored.add(new Scored(page, score));
      }
    }
    scored.sort(Comparator.comparingDouble(Scored::score).reversed()
        .thenComparing(entry -> entry.page().filename())
        .thenComparingInt(entry -> entry.page().pageNumber()));
    List<SourcePage> chosen = new ArrayList<>();
    Set<UUID> usedMaterials = new LinkedHashSet<>();
    int characters = 0;
    for (Scored entry : scored) {
      if (chosen.size() >= MAX_PAGES) break;
      UUID materialId = entry.page().materialId();
      if (!usedMaterials.contains(materialId) && usedMaterials.size() >= MAX_MATERIALS) continue;
      int length = entry.page().text().length();
      if (characters + length > MAX_PACKET_CHARS) { if (!chosen.isEmpty()) continue; length = MAX_PACKET_CHARS; }
      usedMaterials.add(materialId); chosen.add(entry.page()); characters += length;
    }
    return List.copyOf(chosen);
  }

  /** Counts distinct matching terms, each capped so one repeated word cannot dominate. */
  private static int termHits(String text, List<String> terms) {
    if (text == null || text.isBlank()) return 0;
    String haystack = text.toLowerCase(Locale.ROOT);
    int hits = 0;
    for (String term : terms) {
      int from = 0, found = 0;
      while (found < MAX_TERM_HITS) {
        int at = haystack.indexOf(term, from);
        if (at < 0) break;
        found++; from = at + term.length();
      }
      hits += found;
    }
    return hits;
  }

  /** Quotes the selected pages and the question as delimited, untrusted data. */
  private static String userPrompt(String question, List<SourcePage> pages) {
    StringBuilder prompt = new StringBuilder("<course_sources>\n");
    for (SourcePage page : pages) {
      prompt.append("[SOURCE filename=").append(page.filename())
            .append(" page=").append(page.pageNumber()).append("]\n")
            .append(page.text()).append("\n[/SOURCE]\n");
    }
    prompt.append("</course_sources>\n<student_question>\n").append(question).append("\n</student_question>");
    return prompt.toString();
  }

  /**
   * Deterministic grounding check. Returns the cited sources in order, or an
   * empty list when the text cites a filename/page pair outside this request's
   * packet, writes a PDF name in any non-canonical form, cites nothing, or
   * states anything that is not followed by its own citation.
   */
  private static List<Citation> validate(String text, List<SourcePage> packet) {
    if (text == null || text.isBlank()) return List.of();
    Map<String, SourcePage> allowed = new LinkedHashMap<>();
    for (SourcePage page : packet) allowed.put(key(page.filename(), page.pageNumber()), page);

    Map<String, Citation> cited = new LinkedHashMap<>();
    Matcher matcher = CITATION.matcher(text);
    StringBuilder masked = new StringBuilder();
    int cursor = 0;
    while (matcher.find()) {
      masked.append(text, cursor, matcher.start()).append(CITED);
      cursor = matcher.end();
      int page;
      try { page = Integer.parseInt(matcher.group(2)); } catch (NumberFormatException e) { return List.of(); }
      String reference = key(matcher.group(1).trim(), page);
      SourcePage source = allowed.get(reference);
      if (source == null) return List.of();
      cited.putIfAbsent(reference, new Citation(source.materialId(), source.filename(), page));
    }
    masked.append(text.substring(cursor));
    if (ANY_PDF_MENTION.matcher(masked).find()) return List.of();
    if (!everyClaimCited(masked.toString())) return List.of();
    return List.copyOf(cited.values());
  }

  /**
   * Requires each asserted sentence to end with its own citation, so a single
   * valid citation cannot carry an uncited claim beside it. Questions, list
   * lead-ins, and headings assert nothing and are exempt.
   *
   * <p>Runs on the masked text, where every validated citation has already
   * been replaced by one marker character, so a citation's own full stops
   * cannot be mistaken for sentence boundaries.
   */
  private static boolean everyClaimCited(String masked) {
    for (String line : masked.split("\\R")) {
      for (String sentence : SENTENCE_BREAK.split(line)) {
        String claim = sentence.strip();
        if (claim.isEmpty() || claim.startsWith("#") || claim.endsWith("?") || claim.endsWith(":")) continue;
        while (!claim.isEmpty() && ".!,;\"'".indexOf(claim.charAt(claim.length() - 1)) >= 0)
          claim = claim.substring(0, claim.length() - 1).strip();
        if (claim.isEmpty()) continue;
        if (!claim.endsWith(CITED)) return false;
      }
    }
    return true;
  }

  private static String key(String filename, int page) {
    return filename.toLowerCase(Locale.ROOT) + "\u0000" + page;
  }

  /** Neutral, fixed user-facing states; gateway diagnostics never reach a client. */
  static ApiException providerFailure(SoCLaaSClient.Failure failure) {
    return switch (failure) {
      case rate_limited -> ApiException.rateLimited(
          "AI help is busy right now. Wait a moment and try again.");
      case configuration, temporarily_unavailable, rejected -> ApiException.unavailable(
          "AI help is unavailable right now. The forum and course resources still work - try again later.");
    };
  }
}

package io.sacf.tender.service;

import io.sacf.tender.model.KeywordRule;
import io.sacf.tender.model.PncpItem;
import io.sacf.tender.model.SearchMode;
import io.sacf.tender.model.TenderResult;
import io.sacf.tender.model.TenderSearchRequest;
import io.sacf.tender.model.TenderSearchResponse;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TenderSearchService {
  private static final int MAX_SEARCH_DAYS = 31;
  private static final int DEFAULT_MAX_RESULTS = 500;
  private static final int HARD_MAX_RESULTS = 1000;

  private final PncpClient pncpClient;

  public TenderSearchService(PncpClient pncpClient) {
    this.pncpClient = pncpClient;
  }

  public TenderSearchResponse search(TenderSearchRequest request) {
    SearchMode mode = SearchMode.from(request.mode());
    LocalDate start = parseDate(request.startDate(), "startDate");
    LocalDate end = parseDate(request.endDate(), "endDate");
    validateRange(start, end);

    List<MatcherRule> monitored = request.keywordsOrEmpty().stream()
      .filter(rule -> rule.text() != null && !rule.text().isBlank())
      .map(MatcherRule::from)
      .toList();

    if (monitored.isEmpty()) {
      return new TenderSearchResponse(List.of(), 0, mode.name().toLowerCase(), formatDate(start), formatDate(end), Map.of());
    }

    List<Pattern> blocked = request.blockedKeywordsOrEmpty().stream()
      .filter(value -> value != null && !value.isBlank())
      .map(TenderSearchService::makeRegex)
      .toList();

    Set<String> states = normalizeStates(request.statesOrEmpty());
    int maxResults = Math.min(Math.max(request.maxResults() == null ? DEFAULT_MAX_RESULTS : request.maxResults(), 1), HARD_MAX_RESULTS);

    List<TenderResult> results = mode == SearchMode.AWARDED
      ? searchContracts(start, end, monitored, blocked, states, maxResults)
      : searchOpen(start, end, monitored, blocked, states, maxResults);

    Map<String, Integer> keywordHits = new HashMap<>();
    for (TenderResult result : results) {
      for (String keyword : result.matchedKeywords()) {
        keywordHits.merge(keyword, 1, Integer::sum);
      }
    }

    return new TenderSearchResponse(
      results,
      results.size(),
      mode.name().toLowerCase(),
      formatDate(start),
      formatDate(end),
      keywordHits
    );
  }

  private List<TenderResult> searchOpen(
    LocalDate start,
    LocalDate end,
    List<MatcherRule> monitored,
    List<Pattern> blocked,
    Set<String> states,
    int maxResults
  ) {
    Set<String> seen = new HashSet<>();
    List<TenderResult> results = new ArrayList<>();
    LocalDate current = start;

    while (!current.isAfter(end) && results.size() < maxResults) {
      List<PncpItem> items = pncpClient.fetchOpenByDay(formatPncpDate(current));
      for (PncpItem item : items) {
        if (results.size() >= maxResults) break;
        if (isClosed(item.dataEncerramentoProposta())) continue;
        if (!stateAllowed(item.unidadeOrgao() == null ? null : item.unidadeOrgao().ufSigla(), states)) continue;

        String uid = firstNonBlank(
          item.numeroControlePNCP(),
          joinId(item.dataPublicacaoPncp(), item.anoCompra(), item.sequencialCompra())
        );
        if (uid == null || seen.contains(uid)) continue;

        List<String> matched = matchKeywords(buildOpenSearchText(item), monitored, blocked);
        if (matched.isEmpty()) continue;

        seen.add(uid);
        results.add(new TenderResult(
          uid,
          "open",
          uid,
          firstNonBlank(item.objetoCompra(), item.informacaoComplementar(), ""),
          firstNonBlank(orgName(item), unitName(item), ""),
          null,
          null,
          state(item),
          firstNonNull(item.valorTotalEstimado(), item.valorTotalHomologado()),
          item.dataPublicacaoPncp(),
          null,
          item.dataEncerramentoProposta(),
          firstNonBlank(item.linkSistemaOrigem(), item.linkProcessoEletronico(), buildOpenLink(item)),
          matched,
          firstNonBlank(item.modalidadeNome(), "")
        ));
      }
      current = current.plusDays(1);
    }

    return results;
  }

  private List<TenderResult> searchContracts(
    LocalDate start,
    LocalDate end,
    List<MatcherRule> monitored,
    List<Pattern> blocked,
    Set<String> states,
    int maxResults
  ) {
    Set<String> seen = new HashSet<>();
    List<TenderResult> results = new ArrayList<>();
    List<PncpItem> items = pncpClient.fetchContracts(formatPncpDate(start), formatPncpDate(end));

    for (PncpItem item : items) {
      if (results.size() >= maxResults) break;
      if (!stateAllowed(item.unidadeOrgao() == null ? null : item.unidadeOrgao().ufSigla(), states)) continue;

      String uid = firstNonBlank(
        item.numeroControlePNCP(),
        joinId(item.anoContrato(), item.sequencialContrato(), item.niFornecedor())
      );
      if (uid == null || seen.contains(uid)) continue;

      List<String> matched = matchKeywords(buildContractSearchText(item), monitored, blocked);
      if (matched.isEmpty()) continue;

      seen.add(uid);
      results.add(new TenderResult(
        uid,
        "awarded",
        uid,
        firstNonBlank(item.objetoContrato(), item.informacaoComplementar(), ""),
        firstNonBlank(orgName(item), unitName(item), ""),
        firstNonBlank(item.nomeRazaoSocialFornecedor(), item.nomeFornecedorSubContratado(), ""),
        firstNonBlank(item.niFornecedor(), item.niFornecedorSubContratado(), ""),
        state(item),
        firstNonNull(item.valorGlobal(), item.valorInicial(), item.valorAcumulado()),
        firstNonBlank(item.dataPublicacaoPncp(), item.dataAssinatura()),
        item.dataAssinatura(),
        item.dataVigenciaFim(),
        buildContractLink(item),
        matched,
        firstNonBlank(
          item.tipoContrato() == null ? null : item.tipoContrato().nome(),
          item.categoriaProcesso() == null ? null : item.categoriaProcesso().nome(),
          "Contrato"
        )
      ));
    }

    return results;
  }

  private static List<String> matchKeywords(String text, List<MatcherRule> monitored, List<Pattern> blocked) {
    if (blocked.stream().anyMatch(pattern -> pattern.matcher(text).find())) return List.of();

    return monitored.stream()
      .filter(rule -> {
        if (!rule.pattern().matcher(text).find()) return false;
        if (rule.qualifiers().isEmpty()) return true;
        return rule.qualifiers().stream().anyMatch(qualifier -> qualifier.matcher(text).find());
      })
      .map(MatcherRule::text)
      .toList();
  }

  private static String buildOpenSearchText(PncpItem item) {
    return normalize(String.join(" ",
      nullToEmpty(item.objetoCompra()),
      nullToEmpty(item.informacaoComplementar()),
      nullToEmpty(orgName(item)),
      nullToEmpty(unitName(item)),
      nullToEmpty(item.unidadeOrgao() == null ? null : item.unidadeOrgao().ufNome())
    ));
  }

  private static String buildContractSearchText(PncpItem item) {
    return normalize(String.join(" ",
      nullToEmpty(item.objetoContrato()),
      nullToEmpty(item.informacaoComplementar()),
      nullToEmpty(item.nomeRazaoSocialFornecedor()),
      nullToEmpty(item.nomeFornecedorSubContratado()),
      nullToEmpty(orgName(item)),
      nullToEmpty(unitName(item)),
      nullToEmpty(item.unidadeOrgao() == null ? null : item.unidadeOrgao().ufNome())
    ));
  }

  private static Pattern makeRegex(String text) {
    String escaped = List.of(normalize(text).trim().split("\\s+")).stream()
      .filter(part -> !part.isBlank())
      .map(Pattern::quote)
      .collect(Collectors.joining("\\s+"));
    return Pattern.compile("(?:^|[^a-z0-9])" + escaped + "(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
  }

  private static String normalize(String text) {
    String normalized = Normalizer.normalize(nullToEmpty(text), Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "");
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static boolean isClosed(String dateValue) {
    if (dateValue == null || dateValue.isBlank()) return false;
    try {
      return OffsetDateTime.parse(dateValue).toLocalDate().isBefore(LocalDate.now());
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDate.parse(dateValue.substring(0, 10)).isBefore(LocalDate.now());
      } catch (RuntimeException ignoredAgain) {
        return false;
      }
    }
  }

  private static void validateRange(LocalDate start, LocalDate end) {
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("startDate must be before or equal to endDate");
    }
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    if (days > MAX_SEARCH_DAYS) {
      throw new IllegalArgumentException("The maximum search range is " + MAX_SEARCH_DAYS + " days");
    }
  }

  private static LocalDate parseDate(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    String trimmed = value.trim();
    try {
      if (trimmed.matches("\\d{8}")) {
        return LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE);
      }
      return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException error) {
      throw new IllegalArgumentException(fieldName + " must use YYYY-MM-DD or YYYYMMDD");
    }
  }

  private static Set<String> normalizeStates(List<String> values) {
    Set<String> states = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null) continue;
      String state = value.trim().toUpperCase(Locale.ROOT);
      if (state.matches("[A-Z]{2}")) states.add(state);
    }
    return states;
  }

  private static boolean stateAllowed(String state, Set<String> allowed) {
    if (allowed.isEmpty()) return true;
    return state != null && allowed.contains(state.toUpperCase(Locale.ROOT));
  }

  private static String formatDate(LocalDate date) {
    return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private static String formatPncpDate(LocalDate date) {
    return date.format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  private static String buildOpenLink(PncpItem item) {
    String cnpj = item.orgaoEntidade() == null ? null : item.orgaoEntidade().cnpj();
    Object unit = item.unidadeOrgao() == null ? "1" : firstNonBlank(String.valueOf(item.unidadeOrgao().codigoUnidade()), "1");
    if (isBlank(cnpj) || item.anoCompra() == null || item.sequencialCompra() == null) return null;
    return "https://pncp.gov.br/app/editais/" + cnpj + "/" + unit + "/" + item.anoCompra() + "/" + item.sequencialCompra();
  }

  private static String buildContractLink(PncpItem item) {
    String cnpj = item.orgaoEntidade() == null ? null : item.orgaoEntidade().cnpj();
    if (isBlank(cnpj) && item.orgaoSubRogado() != null) cnpj = item.orgaoSubRogado().cnpj();
    if (isBlank(cnpj) || item.anoContrato() == null || item.sequencialContrato() == null) return null;
    return "https://pncp.gov.br/app/contratos/" + cnpj + "/" + item.anoContrato() + "/" + item.sequencialContrato();
  }

  private static String orgName(PncpItem item) {
    return item.orgaoEntidade() == null ? null : item.orgaoEntidade().razaoSocial();
  }

  private static String unitName(PncpItem item) {
    return item.unidadeOrgao() == null ? null : item.unidadeOrgao().nomeUnidade();
  }

  private static String state(PncpItem item) {
    return item.unidadeOrgao() == null ? "" : nullToEmpty(item.unidadeOrgao().ufSigla());
  }

  private static BigDecimal firstNonNull(BigDecimal... values) {
    for (BigDecimal value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private static String firstNonBlank(Object... values) {
    for (Object value : values) {
      if (value == null) continue;
      String text = String.valueOf(value);
      if (!text.isBlank() && !"null".equalsIgnoreCase(text)) return text;
    }
    return null;
  }

  private static String joinId(Object... values) {
    List<String> parts = new ArrayList<>();
    for (Object value : values) {
      String text = firstNonBlank(value);
      if (text != null) parts.add(text);
    }
    return parts.isEmpty() ? null : String.join("-", parts);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record MatcherRule(String text, Pattern pattern, List<Pattern> qualifiers) {
    static MatcherRule from(KeywordRule rule) {
      return new MatcherRule(
        rule.text(),
        makeRegex(rule.text()),
        rule.qualifiersOrEmpty().stream()
          .filter(value -> value != null && !value.isBlank())
          .map(TenderSearchService::makeRegex)
          .toList()
      );
    }
  }
}

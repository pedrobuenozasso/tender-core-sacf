package io.sacf.tender.controller;

import io.sacf.tender.model.HealthResponse;
import io.sacf.tender.model.KeywordRule;
import io.sacf.tender.model.TenderSearchRequest;
import io.sacf.tender.model.TenderSearchResponse;
import io.sacf.tender.service.TenderSearchService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TenderController {
  private final TenderSearchService searchService;

  public TenderController(TenderSearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/health")
  HealthResponse health() {
    return new HealthResponse("ok", "sacf-tender-core", Instant.now());
  }

  @PostMapping("/tenders/search")
  TenderSearchResponse search(@Valid @RequestBody TenderSearchRequest request) {
    return searchService.search(request);
  }

  @GetMapping("/tenders/open")
  TenderSearchResponse open(
    @RequestParam String startDate,
    @RequestParam String endDate,
    @RequestParam String keywords,
    @RequestParam(required = false) String blockedKeywords,
    @RequestParam(required = false) String states,
    @RequestParam(required = false) Integer maxResults
  ) {
    return searchService.search(new TenderSearchRequest(
      "open",
      startDate,
      endDate,
      parseKeywords(keywords),
      parseList(blockedKeywords),
      parseList(states),
      maxResults
    ));
  }

  @GetMapping("/tenders/contracts")
  TenderSearchResponse contracts(
    @RequestParam String startDate,
    @RequestParam String endDate,
    @RequestParam String keywords,
    @RequestParam(required = false) String blockedKeywords,
    @RequestParam(required = false) String states,
    @RequestParam(required = false) Integer maxResults
  ) {
    return searchService.search(new TenderSearchRequest(
      "awarded",
      startDate,
      endDate,
      parseKeywords(keywords),
      parseList(blockedKeywords),
      parseList(states),
      maxResults
    ));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException error) {
    return ResponseEntity.badRequest().body(new ApiError(error.getMessage()));
  }

  private static List<KeywordRule> parseKeywords(String value) {
    return parseList(value).stream()
      .map(keyword -> new KeywordRule(keyword, List.of()))
      .toList();
  }

  private static List<String> parseList(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(","))
      .map(String::trim)
      .filter(item -> !item.isBlank())
      .toList();
  }

  private record ApiError(String error) {
  }
}

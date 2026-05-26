package io.sacf.tender.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TenderSearchRequest(
  String mode,
  String startDate,
  String endDate,
  @Valid @Size(max = 50) List<KeywordRule> keywords,
  @Size(max = 50) List<String> blockedKeywords,
  @Size(max = 27) List<String> states,
  Integer maxResults
) {
  public List<KeywordRule> keywordsOrEmpty() {
    return keywords == null ? List.of() : keywords;
  }

  public List<String> blockedKeywordsOrEmpty() {
    return blockedKeywords == null ? List.of() : blockedKeywords;
  }

  public List<String> statesOrEmpty() {
    return states == null ? List.of() : states;
  }
}

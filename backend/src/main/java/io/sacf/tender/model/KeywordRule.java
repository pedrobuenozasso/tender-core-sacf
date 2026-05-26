package io.sacf.tender.model;

import java.util.List;

public record KeywordRule(
  String text,
  List<String> qualifiers
) {
  public List<String> qualifiersOrEmpty() {
    return qualifiers == null ? List.of() : qualifiers;
  }
}

package io.sacf.tender.model;

import java.util.List;
import java.util.Map;

public record TenderSearchResponse(
  List<TenderResult> results,
  int total,
  String mode,
  String startDate,
  String endDate,
  Map<String, Integer> keywordHits
) {
}

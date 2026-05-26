package io.sacf.tender.model;

import java.math.BigDecimal;
import java.util.List;

public record TenderResult(
  String id,
  String type,
  String pncpControlNumber,
  String object,
  String agency,
  String supplier,
  String supplierDocument,
  String state,
  BigDecimal estimatedValue,
  String publicationDate,
  String signatureDate,
  String closingDate,
  String pncpLink,
  List<String> matchedKeywords,
  String modality
) {
}

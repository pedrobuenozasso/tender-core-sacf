package io.sacf.tender.model;

public enum SearchMode {
  OPEN,
  AWARDED;

  public static SearchMode from(String value) {
    if (value == null || value.isBlank()) return OPEN;
    return switch (value.trim().toLowerCase()) {
      case "open", "abertas", "aberta" -> OPEN;
      case "awarded", "ganhadores", "ganhador", "contratos" -> AWARDED;
      default -> throw new IllegalArgumentException("Invalid search mode: " + value);
    };
  }
}

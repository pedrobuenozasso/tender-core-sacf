package io.sacf.tender.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PncpListResponse(
  List<PncpItem> data,
  Integer totalPaginas
) {
}

package io.sacf.tender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sacf.tender.model.PncpItem;
import io.sacf.tender.model.PncpListResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PncpClient {
  private static final int PAGE_SIZE = 50;
  private static final int MAX_PAGES_PER_MODALITY = 40;
  private static final int CONTRACT_PAGE_SIZE = 500;
  private static final int MAX_CONTRACT_PAGES = 20;
  private static final int CONTRACT_PAGE_BATCH = 10;
  private static final List<Integer> MODALITY_CODES = List.of(1, 2, 4, 5, 6, 8, 12);

  private final String baseUrl;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  public PncpClient(
    @Value("${tender.pncp-base-url:https://pncp.gov.br/api/consulta/v1}") String baseUrl,
    ObjectMapper mapper
  ) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.mapper = mapper;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .build();
  }

  public List<PncpItem> fetchOpenByDay(String dateKey) {
    return MODALITY_CODES.stream()
      .map(code -> CompletableFuture.supplyAsync(() -> fetchModality(dateKey, code)))
      .toList()
      .stream()
      .flatMap(future -> future.join().stream())
      .toList();
  }

  public List<PncpItem> fetchContracts(String startDate, String endDate) {
    PageResult first = fetchContractPage(startDate, endDate, 1);
    List<PncpItem> results = new ArrayList<>(first.items());
    int totalPages = first.totalPages() > 0
      ? Math.min(first.totalPages(), MAX_CONTRACT_PAGES)
      : (first.items().size() < CONTRACT_PAGE_SIZE ? 1 : MAX_CONTRACT_PAGES);

    for (int start = 2; start <= totalPages; start += CONTRACT_PAGE_BATCH) {
      int end = Math.min(totalPages, start + CONTRACT_PAGE_BATCH - 1);
      List<CompletableFuture<PageResult>> batch = new ArrayList<>();
      for (int page = start; page <= end; page++) {
        int pageNumber = page;
        batch.add(CompletableFuture.supplyAsync(() -> fetchContractPage(startDate, endDate, pageNumber)));
      }
      batch.stream()
        .map(CompletableFuture::join)
        .flatMap(page -> page.items().stream())
        .forEach(results::add);
    }

    return results;
  }

  private List<PncpItem> fetchModality(String dateKey, int modality) {
    List<PncpItem> results = new ArrayList<>();

    for (int page = 1; page <= MAX_PAGES_PER_MODALITY; page++) {
      String url = baseUrl + "/contratacoes/publicacao"
        + "?dataInicial=" + encode(dateKey)
        + "&dataFinal=" + encode(dateKey)
        + "&pagina=" + page
        + "&tamanhoPagina=" + PAGE_SIZE
        + "&codigoModalidadeContratacao=" + modality;

      PageResult response = fetchPage(url, Duration.ofSeconds(8));
      results.addAll(response.items());
      if (response.items().size() < PAGE_SIZE) break;
    }

    return results;
  }

  private PageResult fetchContractPage(String startDate, String endDate, int page) {
    String url = baseUrl + "/contratos"
      + "?dataInicial=" + encode(startDate)
      + "&dataFinal=" + encode(endDate)
      + "&pagina=" + page
      + "&tamanhoPagina=" + CONTRACT_PAGE_SIZE;
    return fetchPage(url, Duration.ofSeconds(15));
  }

  private PageResult fetchPage(String url, Duration timeout) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(timeout)
        .header("Accept", "application/json")
        .header("User-Agent", "SACF-TenderCore/1.0")
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return new PageResult(List.of(), 0);
      }

      String body = response.body().trim();
      if (body.startsWith("[")) {
        PncpItem[] items = mapper.readValue(body, PncpItem[].class);
        return new PageResult(Arrays.asList(items), 0);
      }

      PncpListResponse list = mapper.readValue(body, PncpListResponse.class);
      List<PncpItem> items = list.data() == null ? List.of() : list.data();
      int totalPages = list.totalPaginas() == null ? 0 : list.totalPaginas();
      return new PageResult(items, totalPages);
    } catch (IOException | InterruptedException | RuntimeException error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new PageResult(List.of(), 0);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record PageResult(List<PncpItem> items, int totalPages) {
  }
}

package io.sacf.tender.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PncpItem(
  String numeroControlePNCP,
  String objetoCompra,
  String objetoContrato,
  String informacaoComplementar,
  String modalidadeNome,
  String linkSistemaOrigem,
  String linkProcessoEletronico,
  Object anoCompra,
  Object sequencialCompra,
  Object anoContrato,
  Object sequencialContrato,
  String niFornecedor,
  String niFornecedorSubContratado,
  String nomeRazaoSocialFornecedor,
  String nomeFornecedorSubContratado,
  BigDecimal valorTotalEstimado,
  BigDecimal valorTotalHomologado,
  BigDecimal valorGlobal,
  BigDecimal valorInicial,
  BigDecimal valorAcumulado,
  String dataPublicacaoPncp,
  String dataAssinatura,
  String dataEncerramentoProposta,
  String dataVigenciaFim,
  Organization orgaoEntidade,
  Organization orgaoSubRogado,
  Unit unidadeOrgao,
  NamedValue tipoContrato,
  NamedValue categoriaProcesso
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Organization(String cnpj, String razaoSocial) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Unit(Object codigoUnidade, String nomeUnidade, String ufNome, String ufSigla) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NamedValue(String nome) {
  }
}

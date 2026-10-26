package br.com.murkyweb.unesc_share.interacao.avaliacao.dto;

import java.math.BigDecimal;

public record AvaliacaoResumoResponse(BigDecimal media, long quantidade) { }

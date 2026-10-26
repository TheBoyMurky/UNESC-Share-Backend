package br.com.murkyweb.unesc_share.interacao.avaliacao.repository;

import java.math.BigDecimal;

/** SUM de BigDecimal e COUNT na mesma consulta, sem AVG convertido em double. */
public interface AvaliacaoTotais {
	BigDecimal getSoma();
	long getQuantidade();
}

package br.com.murkyweb.unesc_share.interacao.avaliacao.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AvaliacaoRequest(
		@NotNull @DecimalMin("0.00") @DecimalMax("5.00") @Digits(integer = 1, fraction = 2) BigDecimal nota) {
	@Override public String toString() { return "AvaliacaoRequest[nota=REDACTED]"; }
}

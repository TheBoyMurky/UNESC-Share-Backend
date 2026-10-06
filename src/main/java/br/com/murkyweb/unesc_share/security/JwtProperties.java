package br.com.murkyweb.unesc_share.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("unesc-share.security.jwt")
public record JwtProperties(
		@NotBlank String secret,
		@NotBlank String issuer,
		@NotBlank String audience,
		@NotNull @DurationMin(seconds = 60) @DurationMax(minutes = 30) Duration ttl) {

	@Override
	public String toString() {
		return "JwtProperties[secret=REDACTED, issuer=" + issuer + ", audience=" + audience + ", ttl=" + ttl + "]";
	}
}

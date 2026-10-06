package br.com.murkyweb.unesc_share.security.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {

	@Override
	public String toString() {
		return "TokenResponse[accessToken=REDACTED, tokenType=" + tokenType
				+ ", expiresIn=" + expiresIn + ", expiresAt=" + expiresAt + "]";
	}
}

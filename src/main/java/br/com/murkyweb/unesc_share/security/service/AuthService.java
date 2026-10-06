package br.com.murkyweb.unesc_share.security.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.security.dto.LoginRequest;
import br.com.murkyweb.unesc_share.security.dto.TokenResponse;

@Service
public class AuthService {

	private final AuthenticationManager authenticationManager;
	private final JwtEncoder encoder;
	private final JwtProperties properties;
	private final Clock clock;

	public AuthService(AuthenticationManager authenticationManager, JwtEncoder encoder,
			JwtProperties properties, Clock clock) {
		this.authenticationManager = authenticationManager;
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse login(LoginRequest request) {
		var authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.senha()));
		var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		var expiration = now.plusSeconds(properties.ttl().toSeconds());
		var roles = authentication.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.filter(authority -> authority.startsWith("ROLE_"))
				.map(authority -> authority.substring(5)).toList();
		var claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(authentication.getName())
				.issuedAt(now)
				.notBefore(now)
				.expiresAt(expiration)
				.claim("roles", roles)
				.build();
		var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
		return new TokenResponse(token.getTokenValue(), "Bearer", properties.ttl().toSeconds(), expiration);
	}
}

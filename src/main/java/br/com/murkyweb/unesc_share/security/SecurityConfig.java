package br.com.murkyweb.unesc_share.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				// A API aceita tokens somente no header Authorization, nao em cookies.
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> {
					// Matchers sao relativos ao context-path /api/v1.
					auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					if (environment.acceptsProfiles(Profiles.of("dev"))) {
						auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
					}
					auth.requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login").permitAll();
					auth.requestMatchers("/denuncias/moderacao", "/denuncias/moderacao/**").hasAnyRole("MODERADOR", "ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.GET, "/materiais/moderacao").hasAnyRole("MODERADOR", "ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.GET, "/materiais/*/comentarios/moderacao",
							"/materiais/*/comentarios/*/historico-moderacao").hasAnyRole("MODERADOR", "ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.POST, "/materiais/*/comentarios/*/ocultacao",
							"/materiais/*/comentarios/*/reexibicao").hasAnyRole("MODERADOR", "ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.POST, "/materiais/*/publicacao", "/materiais/*/bloqueio")
							.hasAnyRole("MODERADOR", "ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.POST, "/instituicoes", "/instituicoes/**", "/cursos", "/cursos/**",
							"/disciplinas", "/disciplinas/**", "/categorias", "/categorias/**")
							.hasRole("ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.PUT, "/instituicoes/**", "/cursos/**", "/disciplinas/**", "/categorias/**")
							.hasRole("ADMINISTRADOR");
					auth.requestMatchers(HttpMethod.DELETE, "/instituicoes/**", "/cursos/**", "/disciplinas/**", "/categorias/**")
							.hasRole("ADMINISTRADOR");
					auth.anyRequest().authenticated();
				})
				.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
						.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// BCrypt por padrao, com identificador do algoritmo para permitir evolucao.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
		var provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(encoder);
		return new ProviderManager(provider);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(properties.secret());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("JWT_SECRET deve estar codificado em Base64.");
		}
		if (bytes.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes aleatorios antes do Base64.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey key) {
		return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey key, JwtProperties properties, Clock clock) {
		var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		var timestamp = new JwtTimestampValidator(Duration.ZERO);
		timestamp.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				timestamp,
				new JwtIssuerValidator(properties.issuer()),
				new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(properties.audience())),
				new JwtClaimValidator<Instant>("exp", exp -> exp != null),
				new JwtClaimValidator<String>("sub", sub -> sub != null && sub.matches(
						"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))));
		return decoder;
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		var authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${unesc-share.cors.allowed-origins}") List<String> origins) {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Permite ao frontend ler estes cabecalhos de resposta, sem ampliar origens ou permissoes.
		configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}

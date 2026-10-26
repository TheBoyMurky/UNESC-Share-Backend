package br.com.murkyweb.unesc_share.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
@Profile("dev")
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI unescShareOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("UnescShare API")
						.version("v1")
						.description("API REST do catalogo academico UnescShare. O sistema cataloga metadados e referencias "
								+ "BitTorrent; nao hospeda os arquivos academicos compartilhados."))
				.servers(List.of(new Server().url("/api/v1").description("API v1")))
				.components(new Components().addSecuritySchemes(BEARER_AUTH,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}
}

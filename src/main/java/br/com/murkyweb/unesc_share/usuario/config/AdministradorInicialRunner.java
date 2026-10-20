package br.com.murkyweb.unesc_share.usuario.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.service.AdministradorInicialService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "unesc-share.admin-inicial.enabled", havingValue = "true")
@EnableConfigurationProperties(AdministradorInicialProperties.class)
public class AdministradorInicialRunner implements CommandLineRunner {

	private final AdministradorInicialService service;
	private final AdministradorInicialProperties properties;

	public AdministradorInicialRunner(AdministradorInicialService service, AdministradorInicialProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Override
	public void run(String... args) {
		boolean criado = service.provisionar(new CadastroRequest(properties.nome(), properties.email(), properties.senha()));
		if (criado) {
			log.info("Administrador inicial criado. Desative ADMIN_SEED_ENABLED apos a configuracao local.");
		} else {
			log.info("Administrador inicial ja existente; nenhuma credencial ou perfil foi alterado.");
		}
	}
}

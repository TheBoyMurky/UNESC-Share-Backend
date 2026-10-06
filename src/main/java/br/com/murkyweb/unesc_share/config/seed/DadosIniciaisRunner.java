package br.com.murkyweb.unesc_share.config.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "unesc-share.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DadosIniciaisRunner implements CommandLineRunner {

	private final DadosIniciaisService dadosIniciaisService;

	@Override
	public void run(String... args) {
		int criadas = dadosIniciaisService.popular();
		log.info("Dados iniciais UNESC verificados: {} disciplinas criadas.", criadas);
	}
}

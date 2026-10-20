package br.com.murkyweb.unesc_share.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.murkyweb.unesc_share.usuario.config.AdministradorInicialProperties;
import br.com.murkyweb.unesc_share.usuario.config.AdministradorInicialRunner;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import br.com.murkyweb.unesc_share.usuario.service.AdministradorInicialService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(OutputCaptureExtension.class)
class AdministradorInicialConfigTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(AdministradorInicialRunner.class, AdministradorInicialService.class, Dependencias.class);

	@Test
	void semDevNaoRegistraRunnerNemServicoMesmoComFlagHabilitada() {
		contextRunner.withPropertyValues("spring.profiles.active=prod", "unesc-share.admin-inicial.enabled=true")
				.run(context -> {
					assertThat(context).hasNotFailed().doesNotHaveBean(AdministradorInicialRunner.class)
							.doesNotHaveBean(AdministradorInicialService.class).doesNotHaveBean(AdministradorInicialProperties.class);
					verifyNoInteractions(context.getBean(UsuarioRepository.class));
				});
	}

	@Test
	void emDevFicaDesativadoQuandoFlagAusente() {
		contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
			assertThat(context).hasNotFailed().doesNotHaveBean(AdministradorInicialRunner.class)
					.doesNotHaveBean(AdministradorInicialProperties.class);
			verifyNoInteractions(context.getBean(UsuarioRepository.class));
		});
	}

	@Test
	void emDevFlagFalseNaoExigeCredenciais() {
		contextRunner.withPropertyValues("spring.profiles.active=dev", "unesc-share.admin-inicial.enabled=false")
				.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AdministradorInicialRunner.class));
	}

	@Test
	void emDevFlagTrueVinculaPropriedadesEExecutaSemExibirCredenciais(CapturedOutput output) {
		contextRunner.withPropertyValues("spring.profiles.active=dev", "unesc-share.admin-inicial.enabled=true",
				"unesc-share.admin-inicial.nome=NomePrivadoTeste", "unesc-share.admin-inicial.email=privado-config@example.com",
				"unesc-share.admin-inicial.senha=SenhaPrivadaTeste-123").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(AdministradorInicialRunner.class)
					.hasSingleBean(AdministradorInicialProperties.class);
			assertEquals("privado-config@example.com", context.getBean(AdministradorInicialProperties.class).email());
			context.getBean(AdministradorInicialRunner.class).run();
			verify(context.getBean(UsuarioRepository.class)).saveAndFlush(any());
		});
		assertThat(output).contains("Administrador inicial criado.")
				.doesNotContain("NomePrivadoTeste", "privado-config@example.com", "SenhaPrivadaTeste-123");
	}

	@Test
	void habilitadoSemCredenciaisInterrompeExecucaoSemValoresRejeitados(CapturedOutput output) {
		contextRunner.withPropertyValues("spring.profiles.active=dev", "unesc-share.admin-inicial.enabled=true")
				.run(context -> {
					assertThat(context).hasNotFailed().hasSingleBean(AdministradorInicialRunner.class);
					assertThrows(IllegalStateException.class, () -> context.getBean(AdministradorInicialRunner.class).run());
					verifyNoInteractions(context.getBean(UsuarioRepository.class));
				});
		assertThat(output).doesNotContain("Administrador inicial criado.");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Dependencias {
		@Bean
		UsuarioRepository usuarioRepository() { return mock(UsuarioRepository.class); }

		@Bean
		PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

		@Bean(destroyMethod = "close")
		ValidatorFactory validatorFactory() { return Validation.buildDefaultValidatorFactory(); }

		@Bean
		Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
	}
}

package br.com.murkyweb.unesc_share;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UnescShareApplicationTests {
	@Value("${springdoc.api-docs.enabled:false}")
	private boolean apiDocsEnabled;

	@Value("${springdoc.swagger-ui.enabled:false}")
	private boolean swaggerUiEnabled;

	@Test
	void contextLoads() {
	}

	@Test
	void documentacaoFicaDesabilitadaForaDoPerfilDev() {
		assertFalse(apiDocsEnabled);
		assertFalse(swaggerUiEnabled);
	}

}

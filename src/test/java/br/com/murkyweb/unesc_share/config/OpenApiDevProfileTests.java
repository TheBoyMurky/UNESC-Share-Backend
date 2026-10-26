package br.com.murkyweb.unesc_share.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"unesc-share.seed.enabled=false",
		"unesc-share.admin-inicial.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiDevProfileTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void especificacaoOpenApiFicaPublicaSomenteNoPerfilDevEDeclaraBearer() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").isString())
				.andExpect(jsonPath("$.info.title").value("UnescShare API"))
				.andExpect(jsonPath("$.servers[0].url").value("/api/v1"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/usuarios/me'].get.security[0].bearerAuth").exists())
				.andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/materiais/{materialId}/torrents'].post.requestBody.content['multipart/form-data']").exists())
				.andExpect(jsonPath("$.paths['/materiais/{materialId}/torrents'].post.security[0].bearerAuth").exists())
				.andExpect(jsonPath("$.paths['/categorias/{id}'].delete.responses.204.description").value("Categoria excluida."))
				.andExpect(jsonPath("$.paths['/categorias/{id}'].delete.responses.200").doesNotExist());
	}

	@Test
	void interfaceSwaggerUiFicaDisponivelNoPerfilDevSemLiberarRotasDeNegocio() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mvc.perform(get("/usuarios/me")).andExpect(status().isUnauthorized());
	}
}

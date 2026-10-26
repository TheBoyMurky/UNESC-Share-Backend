package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class ComentarioTransacaoTests extends MaterialApiSupport {
	@MockitoSpyBean ComentarioRepository comentarios;

	@Test void falhaAposInserirComentarioReverteRegistroSemExporConteudoOuSql() throws Exception {
		long before = comentarios.count(); var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var id = new AtomicReference<UUID>();
		doAnswer(invocation -> {
			Comentario inserted = invocation.getArgument(0); em.persist(inserted); em.flush(); id.set(inserted.getId());
			assertTrue(comentarios.existsById(id.get()));
			throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
		}).when(comentarios).saveAndFlush(any(Comentario.class));
		var response = mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/comentarios", autor)
				.content(json(new ComentarioRequest("CONTEUDO_PRIVADO_CANARY"))))
				.andExpect(status().isConflict()).andReturn().getResponse();
		assertNotNull(id.get()); assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY"));
		assertFalse(response.getContentAsString().contains("CONTEUDO_PRIVADO_CANARY"));
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus());
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		assertFalse(comentarios.existsById(id.get())); assertEquals(before, comentarios.count());
	}
}

package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

/** Fixture confirmada para observar o rollback real da requisicao; limpeza exata em finally. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComentarioMutacaoTransacaoTests extends MaterialApiSupport {
	@Autowired PlatformTransactionManager manager;
	@MockitoSpyBean ComentarioRepository comentarios;

	private record Fixture(Usuario autor, Base base, Material material, Comentario comentario) { }
	private Fixture fixture() {
		assertFalse(TestTransaction.isActive());
		return new TransactionTemplate(manager).execute(status -> {
			var autor = usuario(PerfilUsuario.USUARIO); var base = base(); var material = material(autor, base, StatusMaterial.PUBLICADO);
			var comentario = comentarios.saveAndFlush(new Comentario("Texto original", autor, material));
			return new Fixture(autor, base, material, comentario);
		});
	}
	private void limpar(Fixture fixture) {
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// Somente UUIDs criados por este teste; nunca catalogo real, deleteAll ou cascatas.
			em.createQuery("delete from Comentario c where c.id = :id").setParameter("id", fixture.comentario().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", fixture.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", fixture.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", fixture.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", fixture.base().instituicao().getId()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id = :id").setParameter("id", fixture.autor().getId()).executeUpdate();
		});
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertFalse(comentarios.existsById(fixture.comentario().getId())); assertFalse(materiais.existsById(fixture.material().getId()));
			assertNull(em.find(Usuario.class, fixture.autor().getId()));
		});
	}
	private void verificarRollback(HttpMethod method) throws Exception {
		var fixture = fixture();
		try {
			var before = new TransactionTemplate(manager).execute(status -> ComentarioResponse.from(
					comentarios.findById(fixture.comentario().getId()).orElseThrow()));
			doAnswer(invocation -> {
				Comentario alterado = invocation.getArgument(0); em.flush();
				if (method == HttpMethod.PUT) { assertEquals("Texto editado", alterado.getConteudo()); }
				else { assertEquals(StatusComentario.REMOVIDO, alterado.getStatus()); }
				// Confirma UPDATE no banco nesta transacao antes de provocar a falha.
				Object valor = em.createNativeQuery(method == HttpMethod.PUT
						? "select conteudo from comentarios where id = :id" : "select status from comentarios where id = :id")
						.setParameter("id", alterado.getId()).getSingleResult();
				assertEquals(method == HttpMethod.PUT ? "Texto editado" : "REMOVIDO", valor.toString());
				throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
			}).when(comentarios).saveAndFlush(any(Comentario.class));
			String path = "/" + fixture.material().getId() + "/comentarios/" + fixture.comentario().getId();
			var response = mvc.perform(req(method, path, fixture.autor()).content(json(new ComentarioRequest("Texto editado"))))
					.andExpect(status().isConflict()).andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY"));
			var after = new TransactionTemplate(manager).execute(status -> ComentarioResponse.from(
					comentarios.findById(fixture.comentario().getId()).orElseThrow()));
			assertEquals(before, after); assertEquals(StatusComentario.ATIVO, after.status());
		} finally { limpar(fixture); }
	}
	@Test void falhaAposUpdateDoTextoRestauraRegistroOriginal() throws Exception { verificarRollback(HttpMethod.PUT); }
	@Test void falhaAposRemocaoLogicaRestauraEstadoOriginal() throws Exception { verificarRollback(HttpMethod.DELETE); }
}

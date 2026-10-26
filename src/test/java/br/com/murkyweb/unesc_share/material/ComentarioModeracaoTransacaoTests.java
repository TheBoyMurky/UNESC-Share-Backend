package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ModeracaoComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

/** Sem transacao de teste externa: falha apos INSERT real deve reverter status e auditoria juntos. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComentarioModeracaoTransacaoTests extends MaterialApiSupport {
	@Autowired PlatformTransactionManager manager;
	@Autowired ComentarioRepository comentarios;
	@MockitoSpyBean ModeracaoComentarioRepository historico;
	private record Fixture(Usuario autor, Usuario equipe, Base base, Material material, Comentario comentario) { }

	private Fixture fixture(StatusComentario inicial) {
		assertFalse(TestTransaction.isActive());
		return new TransactionTemplate(manager).execute(status -> {
			var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
			var base = base(); var material = material(autor, base, StatusMaterial.PUBLICADO);
			var comentario = new Comentario("Texto original", autor, material); comentario.alterarStatus(inicial);
			comentarios.saveAndFlush(comentario); return new Fixture(autor, equipe, base, material, comentario);
		});
	}
	private void limpar(Fixture fixture) {
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// Somente fixtures sinteticas deste teste, removidas por UUID exato e sem cascatas.
			em.createQuery("delete from ModeracaoComentario m where m.comentario.id = :id").setParameter("id", fixture.comentario().getId()).executeUpdate();
			em.createQuery("delete from Comentario c where c.id = :id").setParameter("id", fixture.comentario().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", fixture.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", fixture.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", fixture.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", fixture.base().instituicao().getId()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id in (:autor, :equipe)").setParameter("autor", fixture.autor().getId())
					.setParameter("equipe", fixture.equipe().getId()).executeUpdate();
		});
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertFalse(comentarios.existsById(fixture.comentario().getId())); assertFalse(materiais.existsById(fixture.material().getId()));
			assertNull(em.find(Usuario.class, fixture.autor().getId())); assertNull(em.find(Usuario.class, fixture.equipe().getId()));
		});
	}
	private void verificarRollback(StatusComentario inicial, StatusComentario destino, String acao) throws Exception {
		var fixture = fixture(inicial); var insertedId = new AtomicReference<UUID>();
		try {
			var before = new TransactionTemplate(manager).execute(status -> ComentarioResponse.from(comentarios.findById(fixture.comentario().getId()).orElseThrow()));
			doAnswer(invocation -> {
				ModeracaoComentario registro = invocation.getArgument(0); em.persist(registro); em.flush(); insertedId.set(registro.getId());
				assertEquals(destino.name(), em.createNativeQuery("select status from comentarios where id = :id")
						.setParameter("id", fixture.comentario().getId()).getSingleResult().toString());
				assertEquals(1L, ((Number) em.createNativeQuery("select count(*) from moderacoes_comentarios where id = :id")
						.setParameter("id", insertedId.get()).getSingleResult()).longValue());
				throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
			}).when(historico).saveAndFlush(any(ModeracaoComentario.class));
			String path = "/" + fixture.material().getId() + "/comentarios/" + fixture.comentario().getId() + "/" + acao;
			var response = mvc.perform(req(HttpMethod.POST, path, fixture.equipe()).content(json(new ComentarioModeracaoRequest("MOTIVO_PRIVADO_CANARY"))))
					.andExpect(status().isConflict()).andReturn().getResponse();
			assertNotNull(insertedId.get()); assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY"));
			assertFalse(response.getContentAsString().contains("MOTIVO_PRIVADO_CANARY"));
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				assertEquals(before, ComentarioResponse.from(comentarios.findById(fixture.comentario().getId()).orElseThrow()));
				assertNull(em.find(ModeracaoComentario.class, insertedId.get()));
				assertTrue(historico.findAllByComentarioIdAndComentarioMaterialId(fixture.comentario().getId(), fixture.material().getId(), Pageable.unpaged()).isEmpty());
			});
		} finally { limpar(fixture); }
	}
	@Test void falhaAposOcultacaoEInsercaoDeHistoricoReverteAmbos() throws Exception {
		verificarRollback(StatusComentario.ATIVO, StatusComentario.OCULTO, "ocultacao");
	}
	@Test void falhaAposReexibicaoEInsercaoDeHistoricoReverteAmbos() throws Exception {
		verificarRollback(StatusComentario.OCULTO, StatusComentario.ATIVO, "reexibicao");
	}
}

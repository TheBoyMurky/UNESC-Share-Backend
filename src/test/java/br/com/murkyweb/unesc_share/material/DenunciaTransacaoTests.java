package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DenunciaTransacaoTests extends MaterialApiSupport {
	@Autowired PlatformTransactionManager manager;
	@MockitoSpyBean DenunciaRepository denuncias;
	private record Fixture(Usuario dono, Usuario autor, Base base, Material material) { }
	private Fixture fixture() {
		assertFalse(TestTransaction.isActive());
		return new TransactionTemplate(manager).execute(status -> {
			var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
			return new Fixture(dono, autor, base, material(dono, base, StatusMaterial.PUBLICADO));
		});
	}
	private String path(Fixture f) { return "/" + f.material().getId() + "/denuncias"; }
	private void limpar(Fixture f) {
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// UUIDs exclusivos gerados por este teste, nunca registros reais ou deleteAll/cascatas.
			em.createQuery("delete from Denuncia d where d.material.id = :id").setParameter("id", f.material().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", f.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", f.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", f.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", f.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", f.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", f.base().instituicao().getId()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id in (:dono, :autor)").setParameter("dono", f.dono().getId()).setParameter("autor", f.autor().getId()).executeUpdate();
		});
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertFalse(materiais.existsById(f.material().getId())); assertNull(em.find(Usuario.class, f.autor().getId())); assertNull(em.find(Usuario.class, f.dono().getId()));
			assertTrue(denuncias.findAllByUsuarioId(f.autor().getId(), Pageable.unpaged()).isEmpty());
		});
	}
	@Test void falhaAposInsertRealReverteDenunciaPreservandoMaterial() throws Exception {
		var f = fixture(); var insertedId = new AtomicReference<UUID>();
		try {
			doAnswer(invocation -> {
				Denuncia inserted = invocation.getArgument(0); em.persist(inserted); em.flush(); insertedId.set(inserted.getId());
				assertEquals(1L, ((Number) em.createNativeQuery("select count(*) from denuncias where id = :id")
						.setParameter("id", insertedId.get()).getSingleResult()).longValue());
				throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
			}).when(denuncias).saveAndFlush(any(Denuncia.class));
			var response = mvc.perform(req(HttpMethod.POST, path(f), f.autor()).content("{\"motivo\":\"SPAM\",\"descricao\":\"DESCRICAO_PRIVADA_CANARY\"}"))
					.andExpect(status().isConflict()).andReturn().getResponse();
			assertNotNull(insertedId.get()); assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY"));
			assertFalse(response.getContentAsString().contains("DESCRICAO_PRIVADA_CANARY"));
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				assertFalse(denuncias.existsById(insertedId.get())); assertEquals(StatusMaterial.PUBLICADO, materiais.findById(f.material().getId()).orElseThrow().getStatus());
				assertEquals(1, magnets.countByMaterialId(f.material().getId()));
			});
		} finally { limpar(f); }
	}
	@Test void enviosConcorrentesMesmoUsuarioMaterialCriamUmaUnicaDenunciaAbertaMesmoComMotivosDiferentes() throws Exception {
		var f = fixture();
		try {
			var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
			try (var executor = Executors.newFixedThreadPool(2)) {
				var futures = List.of("SPAM", "OUTRO").stream().map(motivo -> executor.submit(() -> {
					ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
					return mvc.perform(req(HttpMethod.POST, path(f), f.autor()).content("{\"motivo\":\"" + motivo + "\",\"descricao\":\"Relato sintetico\"}"))
							.andReturn().getResponse().getStatus();
				})).toList();
				assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown(); var codes = new java.util.ArrayList<Integer>();
				for (var future : futures) { codes.add(future.get(20, TimeUnit.SECONDS)); }
				assertEquals(List.of(201, 409), codes.stream().sorted().toList());
			}
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				var rows = denuncias.findAllByMaterialId(f.material().getId(), Pageable.unpaged()); assertEquals(1L, rows.getTotalElements());
				assertEquals(StatusDenuncia.PENDENTE, rows.getContent().getFirst().getStatus());
				assertEquals(f.autor().getId(), rows.getContent().getFirst().getUsuario().getId());
				assertEquals(StatusMaterial.PUBLICADO, materiais.findById(f.material().getId()).orElseThrow().getStatus());
			});
		} finally { limpar(f); }
	}
}

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
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvaliacaoTransacaoTests extends MaterialApiSupport {
	@Autowired PlatformTransactionManager manager;
	@MockitoSpyBean AvaliacaoRepository avaliacoes;
	private record Fixture(Usuario dono, Usuario autor, Base base, Material material) { }
	private Fixture fixture() {
		assertFalse(TestTransaction.isActive());
		return new TransactionTemplate(manager).execute(status -> {
			var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
			return new Fixture(dono, autor, base, material(dono, base, StatusMaterial.PUBLICADO));
		});
	}
	private String path(Fixture fixture) { return "/" + fixture.material().getId() + "/avaliacoes"; }
	private void limpar(Fixture fixture) {
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// Material e usuarios sao sinteticos e exclusivos deste teste; nunca dados reais ou deleteAll.
			em.createQuery("delete from Avaliacao a where a.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", fixture.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", fixture.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", fixture.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", fixture.base().instituicao().getId()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id in (:dono, :autor)").setParameter("dono", fixture.dono().getId())
					.setParameter("autor", fixture.autor().getId()).executeUpdate();
		});
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertFalse(materiais.existsById(fixture.material().getId())); assertNull(em.find(Usuario.class, fixture.autor().getId()));
			assertNull(em.find(Usuario.class, fixture.dono().getId()));
			assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(fixture.autor().getId(), fixture.material().getId()));
		});
	}
	@Test void falhaAposInsertRealReverteAvaliacaoSemExporSql() throws Exception {
		var fixture = fixture(); var insertedId = new AtomicReference<UUID>();
		try {
			doAnswer(invocation -> {
				Avaliacao inserted = invocation.getArgument(0); em.persist(inserted); em.flush(); insertedId.set(inserted.getId());
				assertEquals(1L, ((Number) em.createNativeQuery("select count(*) from avaliacoes where id = :id")
						.setParameter("id", insertedId.get()).getSingleResult()).longValue());
				throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
			}).when(avaliacoes).saveAndFlush(any(Avaliacao.class));
			var response = mvc.perform(req(HttpMethod.POST, path(fixture), fixture.autor()).content("{\"nota\":3.75}"))
					.andExpect(status().isConflict()).andReturn().getResponse();
			assertNotNull(insertedId.get()); assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY"));
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				assertFalse(avaliacoes.existsById(insertedId.get()));
				assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(fixture.autor().getId(), fixture.material().getId()));
				assertEquals(StatusMaterial.PUBLICADO, materiais.findById(fixture.material().getId()).orElseThrow().getStatus());
			});
		} finally { limpar(fixture); }
	}
	@Test void duasRequisicoesConcorrentesDoMesmoUsuarioCriamExatamenteUmaNota() throws Exception {
		var fixture = fixture();
		try {
			var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
			try (var executor = Executors.newFixedThreadPool(2)) {
				var futures = List.of("3.75", "4.75").stream().map(nota -> executor.submit(() -> {
					ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
					return mvc.perform(req(HttpMethod.POST, path(fixture), fixture.autor()).content("{\"nota\":" + nota + "}"))
							.andReturn().getResponse().getStatus();
				})).toList();
				assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
				var codes = new java.util.ArrayList<Integer>();
				for (var future : futures) { codes.add(future.get(20, TimeUnit.SECONDS)); }
				assertEquals(List.of(201, 409), codes.stream().sorted().toList());
			}
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				var totais = avaliacoes.resumir(fixture.material().getId(), StatusMaterial.PUBLICADO); assertEquals(1L, totais.getQuantidade());
				var avaliacao = avaliacoes.findByUsuarioIdAndMaterialId(fixture.autor().getId(), fixture.material().getId()).orElseThrow();
				assertTrue(List.of(new java.math.BigDecimal("3.75"), new java.math.BigDecimal("4.75")).contains(avaliacao.getNota()));
				assertEquals(avaliacao.getNota(), totais.getSoma());
			});
		} finally { limpar(fixture); }
	}

	private Avaliacao registrar(Fixture fixture) {
		return new TransactionTemplate(manager).execute(status -> avaliacoes.saveAndFlush(new Avaliacao(
				new java.math.BigDecimal("3.75"), em.find(Usuario.class, fixture.autor().getId()),
				em.find(Material.class, fixture.material().getId()))));
	}
	private AvaliacaoResponse consultar(UUID id) {
		return new TransactionTemplate(manager).execute(status -> AvaliacaoResponse.from(avaliacoes.findById(id).orElseThrow()));
	}
	private void verificarRollbackMutacao(HttpMethod method) throws Exception {
		var fixture = fixture();
		try {
			var avaliacao = registrar(fixture); var before = consultar(avaliacao.getId());
			if (method == HttpMethod.PUT) {
				doAnswer(invocation -> {
					em.flush();
					assertEquals(new java.math.BigDecimal("4.50"), em.createNativeQuery("select nota from avaliacoes where id = :id")
							.setParameter("id", avaliacao.getId()).getSingleResult());
					throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
				}).when(avaliacoes).saveAndFlush(any(Avaliacao.class));
			} else {
				doAnswer(invocation -> {
					em.flush();
					assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from avaliacoes where id = :id")
							.setParameter("id", avaliacao.getId()).getSingleResult()).longValue());
					throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
				}).when(avaliacoes).flush();
			}
			var response = mvc.perform(req(method, path(fixture) + "/minha", fixture.autor()).content("{\"nota\":4.50}"))
					.andExpect(status().isConflict()).andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY")); assertEquals(before, consultar(avaliacao.getId()));
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				var totais = avaliacoes.resumir(fixture.material().getId(), StatusMaterial.PUBLICADO);
				assertEquals(1L, totais.getQuantidade()); assertEquals(new java.math.BigDecimal("3.75"), totais.getSoma());
			});
		} finally { limpar(fixture); }
	}
	@Test void falhaAposUpdateRealRestauraNotaIdEData() throws Exception { verificarRollbackMutacao(HttpMethod.PUT); }
	@Test void falhaAposDeleteFisicoRealRestauraAvaliacao() throws Exception { verificarRollbackMutacao(HttpMethod.DELETE); }

	private void verificarConcorrenciaMutacao(boolean editar) throws Exception {
		var fixture = fixture();
		try {
			var avaliacao = registrar(fixture); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
			try (var executor = Executors.newFixedThreadPool(2)) {
				var metodos = List.of(editar ? HttpMethod.PUT : HttpMethod.DELETE, HttpMethod.DELETE);
				var futures = metodos.stream().map(method -> executor.submit(() -> {
					ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
					return mvc.perform(req(method, path(fixture) + "/minha", fixture.autor()).content("{\"nota\":4.50}"))
							.andReturn().getResponse().getStatus();
				})).toList();
				assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
				int primeiro = futures.get(0).get(20, TimeUnit.SECONDS), segundo = futures.get(1).get(20, TimeUnit.SECONDS);
				assertEquals(204, segundo);
				if (editar) { assertTrue(primeiro == 200 || primeiro == 404); }
				else { assertEquals(204, primeiro); }
			}
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				assertFalse(avaliacoes.existsById(avaliacao.getId()));
				assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(fixture.autor().getId(), fixture.material().getId()));
				assertEquals(0L, avaliacoes.resumir(fixture.material().getId(), StatusMaterial.PUBLICADO).getQuantidade());
			});
		} finally { limpar(fixture); }
	}
	@Test void duasExclusoesConcorrentesSao204SemRessuscitarRegistro() throws Exception { verificarConcorrenciaMutacao(false); }
	@Test void edicaoConcorrenteComExclusaoNaoRessuscitaRegistro() throws Exception { verificarConcorrenciaMutacao(true); }
}

package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.ModeracaoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.ModeracaoDenunciaRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DenunciaModeracaoTransacaoTests extends MaterialApiSupport {
	@Autowired PlatformTransactionManager manager;
	@Autowired DenunciaRepository denuncias;
	@MockitoSpyBean ModeracaoDenunciaRepository historico;
	private record Fixture(Usuario dono, Usuario autor, Usuario a, Usuario b, Base base, Material material, Denuncia denuncia) { }
	private Fixture fixture(StatusDenuncia inicial) {
		assertFalse(TestTransaction.isActive());
		return new TransactionTemplate(manager).execute(status -> {
			var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO);
			var a = usuario(PerfilUsuario.MODERADOR); var b = usuario(PerfilUsuario.ADMINISTRADOR); var base = base();
			var material = material(dono, base, StatusMaterial.PUBLICADO); var d = new Denuncia(MotivoDenuncia.SPAM, "Relato original", autor, material);
			d.alterarStatus(inicial); denuncias.saveAndFlush(d); return new Fixture(dono, autor, a, b, base, material, d);
		});
	}
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dr(Fixture f, String acao, Usuario actor) {
		String path = "/denuncias/moderacao/" + f.denuncia().getId() + "/" + acao;
		return request(HttpMethod.POST, "/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(actor)).content("{\"justificativa\":\"JUSTIFICATIVA_PRIVADA_CANARY\"}");
	}
	private void limpar(Fixture f) {
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// Somente fixtures deste teste, por UUIDs exatos, filhos primeiro; nunca dados reais/cascatas/deleteAll.
			em.createQuery("delete from ModeracaoDenuncia m where m.denuncia.id = :id").setParameter("id", f.denuncia().getId()).executeUpdate();
			em.createQuery("delete from Denuncia d where d.id = :id").setParameter("id", f.denuncia().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", f.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", f.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", f.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", f.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", f.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", f.base().instituicao().getId()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id in :ids").setParameter("ids", List.of(f.dono().getId(), f.autor().getId(), f.a().getId(), f.b().getId())).executeUpdate();
		});
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertFalse(denuncias.existsById(f.denuncia().getId())); assertFalse(materiais.existsById(f.material().getId()));
			for (var u : List.of(f.dono(), f.autor(), f.a(), f.b())) { assertNull(em.find(Usuario.class, u.getId())); }
		});
	}
	private void rollback(StatusDenuncia inicial, StatusDenuncia destino, String acao) throws Exception {
		var f = fixture(inicial); var inserted = new AtomicReference<UUID>();
		try {
			var before = new TransactionTemplate(manager).execute(status -> DenunciaResponse.from(denuncias.findById(f.denuncia().getId()).orElseThrow()));
			doAnswer(invocation -> {
				ModeracaoDenuncia row = invocation.getArgument(0); em.persist(row); em.flush(); inserted.set(row.getId());
				assertEquals(destino.name(), em.createNativeQuery("select status from denuncias where id = :id").setParameter("id", f.denuncia().getId()).getSingleResult().toString());
				assertEquals(1L, ((Number) em.createNativeQuery("select count(*) from moderacoes_denuncias where id = :id").setParameter("id", inserted.get()).getSingleResult()).longValue());
				throw new DataIntegrityViolationException("SQL_PRIVADO_CANARY");
			}).when(historico).saveAndFlush(any(ModeracaoDenuncia.class));
			var response = mvc.perform(dr(f, acao, f.a())).andExpect(status().isConflict()).andReturn().getResponse();
			assertNotNull(inserted.get()); assertFalse(response.getContentAsString().contains("SQL_PRIVADO_CANARY")); assertFalse(response.getContentAsString().contains("JUSTIFICATIVA_PRIVADA_CANARY"));
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				assertEquals(before, DenunciaResponse.from(denuncias.findById(f.denuncia().getId()).orElseThrow()));
				assertNull(em.find(ModeracaoDenuncia.class, inserted.get())); assertTrue(historico.findAllByDenunciaId(f.denuncia().getId(), Pageable.unpaged()).isEmpty());
				assertEquals(StatusMaterial.PUBLICADO, materiais.findById(f.material().getId()).orElseThrow().getStatus());
			});
		} finally { limpar(f); }
	}
	@Test void falhaAposAnaliseEInsertReverteEstadoEAuditoria() throws Exception { rollback(StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE, "analise"); }
	@Test void falhaAposProcedenciaEInsertReverteEstadoEAuditoria() throws Exception { rollback(StatusDenuncia.EM_ANALISE, StatusDenuncia.PROCEDENTE, "procedencia"); }
	@Test void falhaAposImprocedenciaEInsertReverteEstadoEAuditoria() throws Exception { rollback(StatusDenuncia.EM_ANALISE, StatusDenuncia.IMPROCEDENTE, "improcedencia"); }

	@Test void conclusoesConcorrentesDivergentesPermitemUmaDecisaoEUmEvento() throws Exception {
		var f = fixture(StatusDenuncia.EM_ANALISE);
		try {
			var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
			try (var executor = Executors.newFixedThreadPool(2)) {
				var futures = List.of("procedencia", "improcedencia").stream().map(acao -> executor.submit(() -> {
					ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
					return mvc.perform(dr(f, acao, acao.equals("procedencia") ? f.a() : f.b())).andReturn().getResponse().getStatus();
				})).toList();
				assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown(); var codes = new java.util.ArrayList<Integer>();
				for (var future : futures) { codes.add(future.get(20, TimeUnit.SECONDS)); } assertEquals(List.of(200, 409), codes.stream().sorted().toList());
			}
			new TransactionTemplate(manager).executeWithoutResult(status -> {
				var d = denuncias.findById(f.denuncia().getId()).orElseThrow(); assertTrue(List.of(StatusDenuncia.PROCEDENTE, StatusDenuncia.IMPROCEDENTE).contains(d.getStatus()));
				var rows = historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()); assertEquals(1, rows.getTotalElements()); assertEquals(d.getStatus(), rows.getContent().getFirst().getEstadoNovo());
				assertEquals(StatusMaterial.PUBLICADO, materiais.findById(f.material().getId()).orElseThrow().getStatus());
			});
		} finally { limpar(f); }
	}
}

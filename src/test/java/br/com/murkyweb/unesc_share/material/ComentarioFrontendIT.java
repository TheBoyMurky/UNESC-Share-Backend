package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

/** Opt-in: -Dtest=ComentarioFrontendIT. Nao pertence ao padrao *Tests da suite normal. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"server.address=127.0.0.1", "unesc-share.seed.enabled=false", "unesc-share.admin-inicial.enabled=false" })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComentarioFrontendIT extends MaterialApiSupport {
	private static final Path TORRENT_ROOT = temporaryStorage();
	private static Path temporaryStorage() {
		try { return Files.createTempDirectory("unescshare-frontend-it-"); }
		catch (java.io.IOException exception) { throw new IllegalStateException("Nao foi possivel preparar o storage de teste."); }
	}
	@DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
		registry.add("unesc-share.torrent.storage-directory", TORRENT_ROOT::toString);
	}
	@AfterAll static void storageVazio() throws Exception {
		try (var files = Files.list(TORRENT_ROOT)) { assertEquals(0L, files.count(), "Storage de teste deve ficar vazio apos limpeza exata das fixtures."); }
		Files.delete(TORRENT_ROOT);
	}
	@LocalServerPort int port;
	@Autowired PlatformTransactionManager manager;
	private record Fixture(Usuario autor, Usuario equipe, Base base, Material material, String novoNome, String novaSigla) { }

	private Fixture fixture(PerfilUsuario perfil) {
		return new TransactionTemplate(manager).execute(status -> {
			var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(perfil);
			var base = base(); var material = material(autor, base, StatusMaterial.PUBLICADO);
			String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
			String nome = "Instituicao Frontend " + nonce, sigla = "FI" + nonce.substring(0, 24).toUpperCase(java.util.Locale.ROOT);
			assertEquals(0L, em.createQuery("select count(i) from Instituicao i where i.nome = :nome or i.sigla = :sigla", Long.class)
					.setParameter("nome", nome).setParameter("sigla", sigla).getSingleResult());
			return new Fixture(autor, equipe, base, material, nome, sigla);
		});
	}

	private Map<String, Object> ator(Usuario usuario) {
		return Map.of("id", usuario.getId().toString(), "perfil", usuario.getPerfil().name(), "token", token(usuario));
	}

	private void limpar(Fixture fixture) {
		var keys = new TransactionTemplate(manager).execute(status -> em.createQuery("select t.caminhoArquivo from Torrent t where t.material.id = :id", String.class)
				.setParameter("id", fixture.material().getId()).getResultList());
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			// Exclusivamente UUIDs da fixture confirmada; nunca deleteAll, catalogo ou usuarios reais.
			em.createQuery("delete from ModeracaoComentario h where h.comentario.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Comentario c where c.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Torrent t where t.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Magnet m where m.material.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Material m where m.id = :id").setParameter("id", fixture.material().getId()).executeUpdate();
			em.createQuery("delete from Disciplina d where d.id = :id").setParameter("id", fixture.base().disciplina().getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", fixture.base().categoria().getId()).executeUpdate();
			em.createQuery("delete from Curso c where c.id = :id").setParameter("id", fixture.base().curso().getId()).executeUpdate();
			em.createQuery("delete from Instituicao i where i.id = :id").setParameter("id", fixture.base().instituicao().getId()).executeUpdate();
			// O cadastro HTTP pode ter ocorrido mesmo se o cliente perdeu o recibo; nao confiar no UUID retornado.
			em.createQuery("delete from Instituicao i where i.nome = :nome and i.sigla = :sigla")
					.setParameter("nome", fixture.novoNome()).setParameter("sigla", fixture.novaSigla()).executeUpdate();
			em.createQuery("delete from Usuario u where u.id in (:autor, :equipe)")
					.setParameter("autor", fixture.autor().getId()).setParameter("equipe", fixture.equipe().getId()).executeUpdate();
		});
		for (var key : keys) {
			assertTrue(key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.torrent"));
			try { Files.deleteIfExists(TORRENT_ROOT.resolve(key)); }
			catch (java.io.IOException exception) { throw new IllegalStateException("Falha ao limpar metainfo da fixture."); }
			assertFalse(Files.exists(TORRENT_ROOT.resolve(key)));
		}
		new TransactionTemplate(manager).executeWithoutResult(status -> {
			assertNull(em.find(Material.class, fixture.material().getId()));
			assertEquals(0L, em.createQuery("select count(i) from Instituicao i where i.nome = :nome or i.sigla = :sigla", Long.class)
					.setParameter("nome", fixture.novoNome()).setParameter("sigla", fixture.novaSigla()).getSingleResult());
			assertNull(em.find(Usuario.class, fixture.autor().getId())); assertNull(em.find(Usuario.class, fixture.equipe().getId()));
			assertEquals(0L, em.createQuery("select count(c) from Comentario c where c.material.id = :id", Long.class)
					.setParameter("id", fixture.material().getId()).getSingleResult());
			assertEquals(0L, em.createQuery("select count(t) from Torrent t where t.material.id = :id", Long.class)
					.setParameter("id", fixture.material().getId()).getSingleResult());
			assertEquals(0L, em.createQuery("select count(h) from ModeracaoComentario h where h.comentario.material.id = :id", Long.class)
					.setParameter("id", fixture.material().getId()).getSingleResult());
		});
	}

	private void verificar(PerfilUsuario perfil) throws Exception {
		Path frontend = Path.of(System.getProperty("unescshare.frontend-dir", "../UNESC-Share-Frontend")).toAbsolutePath().normalize();
		Path script = frontend.resolve("tests/integration/comentarios-spring.mjs");
		assertTrue(Files.isRegularFile(script), "Repositorio frontend com o roteiro de integracao necessario; confira -Dunescshare.frontend-dir.");
		var fixture = fixture(perfil);
		Process process = null;
		try {
			var builder = new ProcessBuilder("node", script.toString()).directory(frontend.toFile()).redirectErrorStream(true);
			// O Node recebe somente Bearers exclusivos de teste por stdin, sem .env/segredos reais herdados.
			String path = builder.environment().get("PATH"); builder.environment().clear();
			if (path != null) { builder.environment().put("PATH", path); }
			process = builder.start();
			try (var input = process.getOutputStream()) {
				input.write(json(Map.of("apiBaseUrl", "http://127.0.0.1:" + port + "/api/v1", "materialId", fixture.material().getId().toString(), "instituicaoId", fixture.base().instituicao().getId().toString(),
						"autor", ator(fixture.autor()), "equipe", ator(fixture.equipe()), "novaInstituicao", Map.of("nome", fixture.novoNome(), "sigla", fixture.novaSigla()))).getBytes(StandardCharsets.UTF_8));
			}
			assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Tempo excedido no roteiro Node/Spring.");
			String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).trim();
			// Nunca imprimir payload, tokens, respostas ou stack do cliente em uma falha.
			assertEquals(0, process.exitValue(), "Cliente Node/Spring falhou; confira contrato e roteiro de integracao.");
			assertTrue(mapper.readTree(output).path("ok").asBoolean(), "Roteiro integrado nao confirmou sucesso.");
		} finally {
			try {
				if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
			} finally { limpar(fixture); }
		}
	}

	@Test void frontendComModeradorExercitaComentariosModeracaoEHistoricoNoSpringReal() throws Exception { verificar(PerfilUsuario.MODERADOR); }
	@Test void frontendComAdministradorExercitaComentariosModeracaoEHistoricoNoSpringReal() throws Exception { verificar(PerfilUsuario.ADMINISTRADOR); }
}

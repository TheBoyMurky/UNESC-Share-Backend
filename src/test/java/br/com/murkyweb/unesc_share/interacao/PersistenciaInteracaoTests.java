package br.com.murkyweb.unesc_share.interacao;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.validation.Validator;

@ActiveProfiles("test")
@SpringBootTest(properties = "unesc-share.seed.enabled=false")
@Transactional
class PersistenciaInteracaoTests {

	@Autowired private EntityManager em;
	@Autowired private AvaliacaoRepository avaliacoes;
	@Autowired private TorrentRepository torrents;
	@Autowired private MagnetRepository magnets;
	@Autowired private Validator validator;

	@Test
	void persisteNotasDecimaisNosLimitesEPermiteAtualizacao() {
		Material material = criarMaterial();
		for (String valor : List.of("0.00", "3.75", "5.00")) {
			Usuario usuario = criarUsuario();
			Avaliacao avaliacao = avaliacoes.saveAndFlush(new Avaliacao(new BigDecimal(valor), usuario, material));
			UUID id = avaliacao.getId();
			em.clear();
			var encontrada = avaliacoes.findById(id).orElseThrow();
			assertEquals(new BigDecimal(valor), encontrada.getNota());
			assertNotNull(encontrada.getDataCadastro());
			assertTrue(avaliacoes.existsByUsuarioIdAndMaterialId(usuario.getId(), material.getId()));
			assertEquals(id, avaliacoes.findByUsuarioIdAndMaterialId(usuario.getId(), material.getId())
					.orElseThrow().getId());
			encontrada.atualizarNota(new BigDecimal("4.90"));
			em.flush();
			em.clear();
			assertEquals(new BigDecimal("4.90"), avaliacoes.findById(id).orElseThrow().getNota());
		}
	}

	@Test
	void impedeSegundaAvaliacaoDoMesmoUsuarioEMaterial() {
		Material material = criarMaterial();
		avaliacoes.saveAndFlush(new Avaliacao(new BigDecimal("3.75"), material.getUsuario(), material));
		assertThrows(DataIntegrityViolationException.class, () -> avaliacoes.saveAndFlush(
				new Avaliacao(new BigDecimal("2.50"), material.getUsuario(), material)
		));
	}

	@Test
	void constraintPostgresImpedeNotaForaDoIntervaloMesmoViaSql() {
		Material material = criarMaterial();
		em.flush();
		assertThrows(PersistenceException.class, () -> em.createNativeQuery(
				"insert into avaliacoes (id, data_cadastro, nota, usuario_id, material_id) "
						+ "values (:id, current_timestamp, :nota, :usuario, :material)"
		).setParameter("id", UUID.randomUUID())
				.setParameter("nota", new BigDecimal("5.01"))
				.setParameter("usuario", material.getUsuario().getId())
				.setParameter("material", material.getId())
				.executeUpdate());
	}

	@Test
	void validacaoJavaRejeitaNotasNegativasAcimaDeCincoOuComTresCasas() {
		for (String nota : List.of("-0.01", "5.01", "3.755")) {
			assertFalse(validator.validate(new Avaliacao(new BigDecimal(nota), null, null)).isEmpty());
		}
	}

	@Test
	void associaVariosTorrentsEMagnetsEPreservaAoMarcarMaterialRemovido() {
		Material material = criarMaterial();
		for (int i = 0; i < 2; i++) {
			String hash = UUID.randomUUID().toString().replace("-", "") + "01234567";
			torrents.save(new Torrent("exemplo.torrent", "testes/exemplo.torrent", hash, 123, material));
			magnets.save(new Magnet("magnet:?xt=urn:btih:" + hash, hash, material));
		}
		assertEquals(StatusMaterial.PENDENTE, material.getStatus());
		material.alterarStatus(StatusMaterial.REMOVIDO);
		UUID id = material.getId();
		em.flush();
		em.clear();
		assertEquals(StatusMaterial.REMOVIDO, em.find(Material.class, id).getStatus());
		assertEquals(2, torrents.findAllByMaterialIdOrderByDataCadastroAsc(id).size());
		assertEquals(2, magnets.findAllByMaterialIdOrderByDataCadastroAsc(id).size());
	}

	private Material criarMaterial() {
		String sufixo = UUID.randomUUID().toString();
		var instituicao = new Instituicao("Instituição de teste " + sufixo, sufixo.substring(0, 20));
		em.persist(instituicao);
		var curso = new Curso("Curso de teste", null, instituicao);
		em.persist(curso);
		var disciplina = new Disciplina("Disciplina de teste", null, "TEST-001", curso);
		em.persist(disciplina);
		var categoria = new Categoria("Categoria " + sufixo, null);
		em.persist(categoria);
		var material = new Material("Material de teste", "Somente metadados.", criarUsuario(), disciplina, categoria);
		em.persist(material);
		return material;
	}

	private Usuario criarUsuario() {
		var usuario = new Usuario("Usuário de teste", UUID.randomUUID() + "@example.test",
				"hash-sintetico-apenas-para-teste-de-persistencia", PerfilUsuario.USUARIO);
		em.persist(usuario);
		return usuario;
	}
}

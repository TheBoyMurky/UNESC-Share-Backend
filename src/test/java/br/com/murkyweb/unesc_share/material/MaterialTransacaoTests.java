package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetAdicaoRequest;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class MaterialTransacaoTests extends MaterialApiSupport {
	@MockitoSpyBean private MagnetService magnetService;

	@Test
	void falhaDepoisDeInserirMaterialEMagnetsReverteAmbos() throws Exception {
		long materiaisAntes = materiais.count(), magnetsAntes = magnets.count();
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var criado = new AtomicReference<UUID>();
		doAnswer(invocation -> {
			invocation.callRealMethod();
			Material material = invocation.getArgument(0);
			criado.set(material.getId());
			assertTrue(materiais.existsById(material.getId()));
			assertEquals(2, magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).size());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Falha simulada apos flush para verificar atomicidade.");
		}).when(magnetService).associar(any(Material.class), anyList());
		mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, List.of(uri(), uri()))))).andExpect(status().isConflict());
		assertNotNull(criado.get());
		TestTransaction.flagForRollback();
		TestTransaction.end();
		TestTransaction.start();
		assertFalse(materiais.existsById(criado.get()));
		assertEquals(materiaisAntes, materiais.count());
		assertEquals(magnetsAntes, magnets.count());
	}

	@Test
	void falhaAposFlushDoLoteDeAdicaoNaoDeixaNovosMagnets() throws Exception {
		long magnetsAntes = magnets.count();
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var antigo = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst().getId();
		var inseridos = new AtomicReference<List<UUID>>();
		doAnswer(invocation -> {
			invocation.callRealMethod();
			var registros = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId());
			assertEquals(3, registros.size()); assertTrue(magnets.existsById(antigo));
			inseridos.set(registros.stream().map(magnet -> magnet.getId()).filter(id -> !id.equals(antigo)).toList());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Falha simulada apos inserir lote.");
		}).when(magnetService).associar(any(Material.class), anyList());
		mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/magnets", autor)
				.content(json(new MagnetAdicaoRequest(List.of(uri(), uri()))))).andExpect(status().isConflict());
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertNotNull(inseridos.get());
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		for (var id : inseridos.get()) { assertFalse(magnets.existsById(id)); }
		assertEquals(magnetsAntes, magnets.count());
	}
}

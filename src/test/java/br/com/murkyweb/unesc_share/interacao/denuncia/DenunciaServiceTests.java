package br.com.murkyweb.unesc_share.interacao.denuncia;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

class DenunciaServiceTests {
	private final DenunciaRepository denuncias = mock(DenunciaRepository.class);
	private final MaterialRepository materiais = mock(MaterialRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
	private final DenunciaService service = new DenunciaService(denuncias, materiais, usuarios, usuario);
	private final UUID actor = UUID.randomUUID(), materialId = UUID.randomUUID();
	private final DenunciaRequest request = new DenunciaRequest(MotivoDenuncia.SPAM, "DESCRICAO_PRIVADA_CANARY");
	private final List<StatusDenuncia> abertas = List.of(StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE);

	private Material fixture() {
		var autor = mock(Usuario.class); when(autor.getId()).thenReturn(actor); when(usuario.id()).thenReturn(actor);
		when(usuarios.findById(actor)).thenReturn(Optional.of(autor)); var material = mock(Material.class);
		when(material.getStatus()).thenReturn(StatusMaterial.PUBLICADO); when(materiais.buscarParaAtualizacao(materialId)).thenReturn(Optional.of(material));
		return material;
	}
	@Test void ausenciaDoUsuarioNaoBuscaMaterialNemDenuncias() {
		when(usuario.id()).thenReturn(actor);
		assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verifyNoInteractions(materiais, denuncias);
	}
	@Test void materialNaoPublicadoNaoVerificaDuplicidadeNemInsere() {
		var material = fixture(); when(material.getStatus()).thenReturn(StatusMaterial.BLOQUEADO);
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verifyNoInteractions(denuncias);
	}
	@Test void cadastroUsaLockAntesDeVerificarEstadosAbertosEGravaPendenteSemAlterarMaterial() {
		var material = fixture(); when(denuncias.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		var response = service.cadastrar(materialId, request); assertEquals(StatusDenuncia.PENDENTE, response.status());
		var order = inOrder(materiais, denuncias); order.verify(materiais).buscarParaAtualizacao(materialId);
		order.verify(denuncias).existsByUsuarioIdAndMaterialIdAndStatusIn(actor, materialId, abertas);
		order.verify(denuncias).saveAndFlush(any(Denuncia.class)); verify(material, never()).alterarStatus(any());
	}
	@Test void denunciaAbertaRetorna409SemInsereOuAtualizarRelato() {
		fixture(); when(denuncias.existsByUsuarioIdAndMaterialIdAndStatusIn(actor, materialId, abertas)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verify(denuncias, never()).saveAndFlush(any());
	}
	@Test void falhaDePersistenciaNaoExpoeSqlDescricaoOuCausa() {
		fixture(); when(denuncias.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL_PRIVADO_CANARY"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause());
		assertFalse(exception.getMessage().contains("SQL_PRIVADO_CANARY")); assertFalse(exception.getMessage().contains("DESCRICAO_PRIVADA_CANARY"));
	}
	@Test void listagemForcaAutorNaConsultaEContagemSemConsultarMaterial() {
		when(usuario.id()).thenReturn(actor); var page = PageRequest.of(0, 20);
		when(denuncias.findAllByUsuarioId(actor, page)).thenReturn(Page.empty(page)); assertTrue(service.minhas(page).isEmpty());
		verify(denuncias).findAllByUsuarioId(actor, page); verifyNoInteractions(materiais);
	}
	@Test void detalhesForcamParIdAutorNaConsultaSemConsultarMaterial() {
		UUID id = UUID.randomUUID(); when(usuario.id()).thenReturn(actor);
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.buscar(id)).getStatusCode());
		verify(denuncias).findByIdAndUsuarioId(id, actor); verifyNoInteractions(materiais);
	}
	@Test void dtosRedigemDescricaoEmToString() {
		assertFalse(request.toString().contains("DESCRICAO_PRIVADA_CANARY"));
		assertFalse(new DenunciaResponse(null, MotivoDenuncia.SPAM, "DESCRICAO_PRIVADA_CANARY", StatusDenuncia.PENDENTE, null, materialId)
				.toString().contains("DESCRICAO_PRIVADA_CANARY"));
	}
}

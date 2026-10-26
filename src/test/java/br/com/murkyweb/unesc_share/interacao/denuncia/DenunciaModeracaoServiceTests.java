package br.com.murkyweb.unesc_share.interacao.denuncia;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.ModeracaoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.ModeracaoDenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaModeracaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

class DenunciaModeracaoServiceTests {
	private final DenunciaRepository denuncias = mock(DenunciaRepository.class);
	private final ModeracaoDenunciaRepository historico = mock(ModeracaoDenunciaRepository.class);
	private final MaterialRepository materiais = mock(MaterialRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
	private final DenunciaModeracaoService service = new DenunciaModeracaoService(denuncias, historico, materiais, usuarios, usuario);
	private final UUID id = UUID.randomUUID(), materialId = UUID.randomUUID(), actor = UUID.randomUUID();
	private final DenunciaModeracaoRequest request = new DenunciaModeracaoRequest("JUSTIFICATIVA_PRIVADA_CANARY");

	private Denuncia fixture() {
		var responsavel = mock(Usuario.class); when(responsavel.getId()).thenReturn(actor); when(usuario.id()).thenReturn(actor);
		when(usuarios.findById(actor)).thenReturn(Optional.of(responsavel)); when(denuncias.buscarMaterialId(id)).thenReturn(Optional.of(materialId));
		var dono = mock(Usuario.class); when(dono.getId()).thenReturn(UUID.randomUUID()); var autor = mock(Usuario.class); when(autor.getId()).thenReturn(UUID.randomUUID());
		var material = mock(Material.class); when(material.getUsuario()).thenReturn(dono); when(materiais.buscarParaAtualizacao(materialId)).thenReturn(Optional.of(material));
		var d = mock(Denuncia.class); when(d.getMaterial()).thenReturn(material); when(d.getUsuario()).thenReturn(autor); when(d.getStatus()).thenReturn(StatusDenuncia.PENDENTE);
		when(denuncias.buscarParaAtualizacao(id, materialId)).thenReturn(Optional.of(d)); return d;
	}
	@Test void projetaSoMaterialIdDepoisBloqueiaMaterialEDenunciaAntesDeGravarEstadoEHistorico() {
		var d = fixture(); service.analisar(id, request); var order = inOrder(denuncias, materiais, d, historico);
		order.verify(denuncias).buscarMaterialId(id); order.verify(materiais).buscarParaAtualizacao(materialId);
		order.verify(denuncias).buscarParaAtualizacao(id, materialId); order.verify(d).alterarStatus(StatusDenuncia.EM_ANALISE);
		order.verify(denuncias).saveAndFlush(d); order.verify(historico).saveAndFlush(any(ModeracaoDenuncia.class));
		verify(denuncias, never()).findById(any()); verify(d.getMaterial(), never()).alterarStatus(any());
	}
	@Test void conflitoDeInteresseNaoGravaNemMudaEstado() {
		var d = fixture(); when(d.getUsuario().getId()).thenReturn(actor);
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.analisar(id, request)).getStatusCode());
		verify(d, never()).alterarStatus(any()); verify(denuncias, never()).saveAndFlush(any()); verifyNoInteractions(historico);
	}
	@Test void conclusaoDiretaDePendenteNaoGravaHistorico() {
		fixture(); assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.proceder(id, request)).getStatusCode());
		verify(denuncias, never()).saveAndFlush(any()); verifyNoInteractions(historico);
	}
	@Test void responsavelAusenteNaoBuscaLocksOuDenuncia() {
		when(usuario.id()).thenReturn(actor); assertEquals(HttpStatus.UNAUTHORIZED,
				assertThrows(ResponseStatusException.class, () -> service.analisar(id, request)).getStatusCode());
		verifyNoInteractions(denuncias, materiais, historico);
	}
	@Test void falhaDeAuditoriaNaoExpoeSqlJustificativaOuCausa() {
		fixture(); when(historico.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL_PRIVADO_CANARY"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.analisar(id, request));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause()); assertFalse(exception.getMessage().contains("SQL_PRIVADO_CANARY"));
		assertFalse(exception.getMessage().contains("JUSTIFICATIVA_PRIVADA_CANARY"));
	}
	@Test void filaEncaminhaEstadoEPaginacaoParaConsultaEContagem() {
		var page = PageRequest.of(0, 20); when(denuncias.findAllByStatus(StatusDenuncia.EM_ANALISE, page)).thenReturn(Page.empty(page));
		assertTrue(service.listar(StatusDenuncia.EM_ANALISE, page).isEmpty()); verify(denuncias).findAllByStatus(StatusDenuncia.EM_ANALISE, page);
	}
	@Test void historicoDeDenunciaInexistenteNaoConsultaEventos() {
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.historico(id, PageRequest.of(0, 20))).getStatusCode());
		verifyNoInteractions(historico);
	}
	@Test void dtoNaoExpoeJustificativaEmToString() {
		assertFalse(request.toString().contains("JUSTIFICATIVA_PRIVADA_CANARY"));
		assertFalse(new DenunciaModeracaoResponse(null, id, actor, "JUSTIFICATIVA_PRIVADA_CANARY", StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE, null)
				.toString().contains("JUSTIFICATIVA_PRIVADA_CANARY"));
	}
}

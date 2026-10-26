package br.com.murkyweb.unesc_share.interacao.comentario;

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

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ModeracaoComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioModeracaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

class ComentarioModeracaoServiceTests {
	private final ComentarioRepository comentarios = mock(ComentarioRepository.class);
	private final ModeracaoComentarioRepository historico = mock(ModeracaoComentarioRepository.class);
	private final MaterialRepository materiais = mock(MaterialRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
	private final ComentarioModeracaoService service = new ComentarioModeracaoService(comentarios, historico, materiais, usuarios, usuario);
	private final UUID materialId = UUID.randomUUID(), comentarioId = UUID.randomUUID(), actor = UUID.randomUUID();
	private final ComentarioModeracaoRequest request = new ComentarioModeracaoRequest("Motivo privado");

	private Comentario fixture() {
		when(usuario.id()).thenReturn(actor); when(usuarios.findById(actor)).thenReturn(Optional.of(mock(Usuario.class)));
		var material = mock(Material.class); when(material.getStatus()).thenReturn(StatusMaterial.PUBLICADO);
		when(materiais.buscarParaAtualizacao(materialId)).thenReturn(Optional.of(material));
		var comentario = mock(Comentario.class); when(comentario.getStatus()).thenReturn(StatusComentario.ATIVO);
		when(comentario.getMaterial()).thenReturn(material); when(comentario.getUsuario()).thenReturn(mock(Usuario.class));
		when(comentarios.buscarParaAtualizacao(comentarioId, materialId)).thenReturn(Optional.of(comentario));
		return comentario;
	}
	@Test void segueOrdemDeLocksEGravaMudancaAntesDoHistorico() {
		var comentario = fixture(); service.ocultar(materialId, comentarioId, request);
		var order = inOrder(materiais, comentarios, historico, comentario);
		order.verify(materiais).buscarParaAtualizacao(materialId);
		order.verify(comentarios).buscarParaAtualizacao(comentarioId, materialId);
		order.verify(comentario).alterarStatus(StatusComentario.OCULTO);
		order.verify(comentarios).saveAndFlush(comentario); order.verify(historico).saveAndFlush(any(ModeracaoComentario.class));
		verify(comentario, never()).editar(any());
	}
	@Test void falhaDeAuditoriaNaoExpoeMotivoSqlNemCausa() {
		fixture(); when(historico.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL_MOTIVO_PRIVADO"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.ocultar(materialId, comentarioId, request));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause());
		assertFalse(exception.getMessage().contains("SQL_MOTIVO_PRIVADO")); assertFalse(exception.getMessage().contains("Motivo privado"));
	}
	@Test void ausenciaDoAtorPersistidoNaoBuscaLocksNemInsereHistorico() {
		when(usuario.id()).thenReturn(actor);
		assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
				() -> service.ocultar(materialId, comentarioId, request)).getStatusCode());
		verifyNoInteractions(materiais, comentarios, historico);
	}
	@Test void consultaRestritaAplicaListaFixaDeEstadosNoRepository() {
		var page = PageRequest.of(0, 20); when(materiais.existsById(materialId)).thenReturn(true);
		var estados = List.of(StatusComentario.ATIVO, StatusComentario.OCULTO);
		when(comentarios.findAllByMaterialIdAndStatusIn(materialId, estados, page)).thenReturn(Page.empty(page));
		assertTrue(service.listar(materialId, page).isEmpty()); verify(comentarios).findAllByMaterialIdAndStatusIn(materialId, estados, page);
		verifyNoInteractions(historico);
	}
	@Test void historicoDeParInexistenteNaoConsultaRegistros() {
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
				() -> service.historico(materialId, comentarioId, PageRequest.of(0, 20))).getStatusCode());
		verifyNoInteractions(historico);
	}
	@Test void dtosNaoExpoemMotivoEmToString() {
		assertFalse(request.toString().contains("Motivo privado"));
		assertFalse(new ComentarioModeracaoResponse(null, comentarioId, actor, "Motivo privado",
				StatusComentario.ATIVO, StatusComentario.OCULTO, null).toString().contains("Motivo privado"));
	}
}

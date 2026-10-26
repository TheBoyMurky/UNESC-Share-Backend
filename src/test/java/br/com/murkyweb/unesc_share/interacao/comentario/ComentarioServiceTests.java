package br.com.murkyweb.unesc_share.interacao.comentario;

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

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

class ComentarioServiceTests {
	private final ComentarioRepository comentarios = mock(ComentarioRepository.class);
	private final MaterialRepository materiais = mock(MaterialRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
	private final ComentarioService service = new ComentarioService(comentarios, materiais, usuarios, usuario);

	@Test void consultaInvisivelNaoBuscaComentarios() {
		var exception = assertThrows(ResponseStatusException.class, () -> service.listar(UUID.randomUUID(), PageRequest.of(0, 20)));
		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode()); verifyNoInteractions(comentarios);
	}
	@Test void consultaTemPredicadoDeMaterialPublicadoEComentarioAtivoAlemDoPreCheck() {
		UUID id = UUID.randomUUID(); var page = PageRequest.of(0, 20);
		when(materiais.existsByIdAndStatus(id, StatusMaterial.PUBLICADO)).thenReturn(true);
		when(comentarios.findAllByMaterialIdAndMaterialStatusAndStatus(id, StatusMaterial.PUBLICADO, StatusComentario.ATIVO, page))
				.thenReturn(Page.empty(page));
		assertTrue(service.listar(id, page).isEmpty());
		verify(comentarios).findAllByMaterialIdAndMaterialStatusAndStatus(id, StatusMaterial.PUBLICADO, StatusComentario.ATIVO, page);
	}
	@Test void usuarioAusenteNaoInsereNemBuscaMaterial() {
		UUID id = UUID.randomUUID(); when(usuario.id()).thenReturn(id); when(usuarios.findById(id)).thenReturn(Optional.empty());
		var exception = assertThrows(ResponseStatusException.class, () -> service.cadastrar(UUID.randomUUID(), new ComentarioRequest("Texto")));
		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode()); verifyNoInteractions(materiais, comentarios);
	}
	@Test void materialNaoPublicadoNaoInsereEUsaLockDeMutacao() {
		UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
		when(usuario.id()).thenReturn(actor); when(usuarios.findById(actor)).thenReturn(Optional.of(mock(Usuario.class)));
		var material = mock(Material.class); when(material.getStatus()).thenReturn(StatusMaterial.BLOQUEADO);
		when(materiais.buscarParaAtualizacao(id)).thenReturn(Optional.of(material));
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.cadastrar(id, new ComentarioRequest("Texto"))).getStatusCode());
		verify(materiais).buscarParaAtualizacao(id); verifyNoInteractions(comentarios);
	}
	@Test void conflitoDePersistenciaNaoExpoeSqlOuDadosRejeitados() {
		UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
		when(usuario.id()).thenReturn(actor); when(usuarios.findById(actor)).thenReturn(Optional.of(mock(Usuario.class)));
		var material = mock(Material.class); when(material.getStatus()).thenReturn(StatusMaterial.PUBLICADO);
		when(materiais.buscarParaAtualizacao(id)).thenReturn(Optional.of(material));
		when(comentarios.saveAndFlush(any(Comentario.class))).thenThrow(new DataIntegrityViolationException("SQL_CANARY_PRIVADO"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.cadastrar(id, new ComentarioRequest("Texto")));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause());
		assertFalse(exception.getMessage().contains("SQL_CANARY_PRIVADO"));
	}

	private Comentario proprio(UUID materialId, UUID comentarioId, StatusComentario status) {
		UUID actor = UUID.randomUUID(); var autor = mock(Usuario.class); when(autor.getId()).thenReturn(actor); when(usuario.id()).thenReturn(actor);
		var material = mock(Material.class); when(material.getStatus()).thenReturn(StatusMaterial.PUBLICADO);
		var comentario = mock(Comentario.class); when(comentario.getUsuario()).thenReturn(autor);
		when(comentario.getMaterial()).thenReturn(material); when(comentario.getStatus()).thenReturn(status);
		when(materiais.buscarParaAtualizacao(materialId)).thenReturn(Optional.of(material));
		when(comentarios.buscarParaAtualizacao(comentarioId, materialId)).thenReturn(Optional.of(comentario));
		return comentario;
	}
	@Test void comentarioRemovidoNaoERegravadoNemExcluidoFisicamente() {
		UUID materialId = UUID.randomUUID(), id = UUID.randomUUID(); var comentario = proprio(materialId, id, StatusComentario.REMOVIDO);
		service.remover(materialId, id);
		var order = inOrder(materiais, comentarios);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(comentarios).buscarParaAtualizacao(id, materialId);
		verify(comentarios, never()).saveAndFlush(any()); verify(comentarios, never()).delete(any());
		verify(comentario, never()).alterarStatus(any());
	}
	@Test void edicaoUsaOrdemMaterialDepoisComentarioEPreservaEstado() {
		UUID materialId = UUID.randomUUID(), id = UUID.randomUUID(); var comentario = proprio(materialId, id, StatusComentario.ATIVO);
		when(comentarios.saveAndFlush(comentario)).thenReturn(comentario);
		service.atualizar(materialId, id, new ComentarioRequest("Texto editado"));
		var order = inOrder(materiais, comentarios);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(comentarios).buscarParaAtualizacao(id, materialId);
		order.verify(comentarios).saveAndFlush(comentario); verify(comentario).editar("Texto editado");
		verify(comentario, never()).alterarStatus(any());
	}
	@Test void comentarioDeOutroAutorNaoPodeSerAlteradoNemRemovido() {
		UUID materialId = UUID.randomUUID(), id = UUID.randomUUID(); var comentario = proprio(materialId, id, StatusComentario.ATIVO);
		when(usuario.id()).thenReturn(UUID.randomUUID());
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
				() -> service.atualizar(materialId, id, new ComentarioRequest("Texto editado"))).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.remover(materialId, id)).getStatusCode());
		verify(comentarios, never()).saveAndFlush(any()); verify(comentario, never()).editar(any());
		verify(comentario, never()).alterarStatus(any());
	}
}

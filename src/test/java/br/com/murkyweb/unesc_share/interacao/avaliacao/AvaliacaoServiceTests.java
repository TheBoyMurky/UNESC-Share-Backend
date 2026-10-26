package br.com.murkyweb.unesc_share.interacao.avaliacao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoRequest;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoTotais;
import br.com.murkyweb.unesc_share.interacao.avaliacao.service.AvaliacaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

class AvaliacaoServiceTests {
	private final AvaliacaoRepository avaliacoes = mock(AvaliacaoRepository.class);
	private final MaterialRepository materiais = mock(MaterialRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
	private final AvaliacaoService service = new AvaliacaoService(avaliacoes, materiais, usuarios, usuario);
	private final UUID materialId = UUID.randomUUID(), actor = UUID.randomUUID();
	private final AvaliacaoRequest request = new AvaliacaoRequest(new BigDecimal("3.75"));

	private Material fixture() {
		var autor = mock(Usuario.class); when(autor.getId()).thenReturn(actor); when(usuario.id()).thenReturn(actor);
		when(usuarios.findById(actor)).thenReturn(Optional.of(autor)); var dono = mock(Usuario.class); when(dono.getId()).thenReturn(UUID.randomUUID());
		var material = mock(Material.class); when(material.getUsuario()).thenReturn(dono); when(material.getStatus()).thenReturn(StatusMaterial.PUBLICADO);
		when(materiais.buscarParaAtualizacao(materialId)).thenReturn(Optional.of(material)); return material;
	}
	@Test void cadastroUsaLockDoMaterialAntesDeVerificarDuplicidadeEInserir() {
		fixture(); when(avaliacoes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		service.cadastrar(materialId, request); var order = inOrder(materiais, avaliacoes);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(avaliacoes).existsByUsuarioIdAndMaterialId(actor, materialId);
		order.verify(avaliacoes).saveAndFlush(any(Avaliacao.class));
	}
	@Test void naoBuscaMaterialQuandoUsuarioNaoExiste() {
		when(usuario.id()).thenReturn(actor);
		assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verifyNoInteractions(materiais, avaliacoes);
	}
	@Test void materialNaoPublicadoNaoConsultaDuplicidadeNemInsere() {
		var material = fixture(); when(material.getStatus()).thenReturn(StatusMaterial.BLOQUEADO);
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verifyNoInteractions(avaliacoes);
	}
	@Test void autoavaliacaoNaoConsultaDuplicidadeNemInsere() {
		var material = fixture(); when(material.getUsuario().getId()).thenReturn(actor);
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verifyNoInteractions(avaliacoes);
	}
	@Test void duplicidadeNaoAtualizaNotaNemPersisteOutroRegistro() {
		fixture(); when(avaliacoes.existsByUsuarioIdAndMaterialId(actor, materialId)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request)).getStatusCode());
		verify(avaliacoes, never()).saveAndFlush(any());
	}
	@Test void conflitoDoBancoNaoExpoeSqlOuCausa() {
		fixture(); when(avaliacoes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL_PRIVADO_CANARY"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.cadastrar(materialId, request));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause()); assertFalse(exception.getMessage().contains("SQL_PRIVADO_CANARY"));
	}
	@Test void leiturasDeMaterialPrivadoNaoConsultamAvaliacoes() {
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.minha(materialId)).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.resumo(materialId)).getStatusCode());
		verifyNoInteractions(avaliacoes);
	}
	@Test void resumoUsaBigDecimalComDuasCasasEIncluiPublicacaoNaConsulta() {
		when(materiais.existsByIdAndStatus(materialId, StatusMaterial.PUBLICADO)).thenReturn(true); var totais = mock(AvaliacaoTotais.class);
		when(avaliacoes.resumir(materialId, StatusMaterial.PUBLICADO)).thenReturn(totais);
		when(totais.getQuantidade()).thenReturn(3L); when(totais.getSoma()).thenReturn(new BigDecimal("10.00"));
		var resumo = service.resumo(materialId); assertEquals(new BigDecimal("3.33"), resumo.media()); assertEquals(3L, resumo.quantidade());
		when(totais.getQuantidade()).thenReturn(2L); when(totais.getSoma()).thenReturn(new BigDecimal("8.01"));
		assertEquals(new BigDecimal("4.01"), service.resumo(materialId).media());
		when(totais.getQuantidade()).thenReturn(0L); when(totais.getSoma()).thenReturn(null); assertNull(service.resumo(materialId).media());
	}
	@Test void consultaMinhaForcaIdentidadeEEstadoPublicado() {
		when(materiais.existsByIdAndStatus(materialId, StatusMaterial.PUBLICADO)).thenReturn(true); when(usuario.id()).thenReturn(actor);
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.minha(materialId)).getStatusCode());
		verify(avaliacoes).findByUsuarioIdAndMaterialIdAndMaterialStatus(actor, materialId, StatusMaterial.PUBLICADO);
	}
	@Test void dtosNaoExpoemNotaEmToString() {
		assertFalse(request.toString().contains("3.75"));
		assertFalse(new AvaliacaoResponse(null, new BigDecimal("3.75"), null, actor, materialId).toString().contains("3.75"));
	}
	@Test void edicaoBloqueiaMaterialAntesDeBuscarSomentePropriaNotaEPreservaIdentidade() {
		fixture(); var avaliacao = mock(Avaliacao.class); when(avaliacao.getUsuario()).thenReturn(mock(Usuario.class));
		when(avaliacao.getMaterial()).thenReturn(mock(Material.class));
		when(avaliacoes.buscarPropriaParaAtualizacao(actor, materialId)).thenReturn(Optional.of(avaliacao));
		when(avaliacoes.saveAndFlush(avaliacao)).thenReturn(avaliacao);
		service.atualizar(materialId, request); var order = inOrder(materiais, avaliacoes, avaliacao);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(avaliacoes).buscarPropriaParaAtualizacao(actor, materialId);
		order.verify(avaliacao).atualizarNota(new BigDecimal("3.75")); order.verify(avaliacoes).saveAndFlush(avaliacao);
		verify(avaliacoes, never()).delete(any());
	}
	@Test void remocaoAusenteEIdempotenteSemDeleteOuFlush() {
		fixture(); service.remover(materialId); var order = inOrder(materiais, avaliacoes);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(avaliacoes).buscarPropriaParaAtualizacao(actor, materialId);
		verify(avaliacoes, never()).delete(any()); verify(avaliacoes, never()).flush();
	}
	@Test void remocaoDeletaSomenteRegistroEncontradoENaoOMaterial() {
		fixture(); var avaliacao = mock(Avaliacao.class);
		when(avaliacoes.buscarPropriaParaAtualizacao(actor, materialId)).thenReturn(Optional.of(avaliacao));
		service.remover(materialId); var order = inOrder(materiais, avaliacoes);
		order.verify(materiais).buscarParaAtualizacao(materialId); order.verify(avaliacoes).buscarPropriaParaAtualizacao(actor, materialId);
		order.verify(avaliacoes).delete(avaliacao); order.verify(avaliacoes).flush(); verify(materiais, never()).delete(any(Material.class));
	}
	@Test void conflitoNaExclusaoNaoExpoeSqlOuCausa() {
		fixture(); when(avaliacoes.buscarPropriaParaAtualizacao(actor, materialId)).thenReturn(Optional.of(mock(Avaliacao.class)));
		doThrow(new DataIntegrityViolationException("SQL_PRIVADO_CANARY")).when(avaliacoes).flush();
		var exception = assertThrows(ResponseStatusException.class, () -> service.remover(materialId));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); assertNull(exception.getCause()); assertFalse(exception.getMessage().contains("SQL_PRIVADO_CANARY"));
	}
	@Test void materialInexistenteNaoBuscaNemRemoveAvaliacao() {
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.remover(materialId)).getStatusCode());
		verifyNoInteractions(avaliacoes);
	}
}

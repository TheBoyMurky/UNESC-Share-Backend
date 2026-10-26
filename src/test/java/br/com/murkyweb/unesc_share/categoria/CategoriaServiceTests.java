package br.com.murkyweb.unesc_share.categoria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.categoria.dto.CategoriaRequest;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import br.com.murkyweb.unesc_share.categoria.service.CategoriaService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;

class CategoriaServiceTests {

	private CategoriaRepository repository;
	private MaterialRepository materiais;
	private CategoriaService service;

	@BeforeEach
	void configurar() {
		repository = mock(CategoriaRepository.class);
		materiais = mock(MaterialRepository.class);
		service = new CategoriaService(repository, materiais);
	}

	@Test
	void conflitoDeConstraintNoCadastroRetorna409SemExporSql() {
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL interno sensivel"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.cadastrar(new CategoriaRequest("Categoria", null)));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Ja existe categoria com esse nome.", exception.getReason());
		assertNull(exception.getCause());
	}

	@Test
	void conflitoDeConstraintNaEdicaoRetorna409() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Categoria("Categoria", null)));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new CategoriaRequest("Outro Nome", null))).getStatusCode());
	}

	@Test
	void fkConcorrenteNaExclusaoRetorna409SemExcluirMateriais() {
		UUID id = UUID.randomUUID();
		var categoria = new Categoria("Categoria", null);
		when(repository.findById(id)).thenReturn(Optional.of(categoria));
		when(materiais.existsByCategoriaId(id)).thenReturn(false);
		doThrow(new DataIntegrityViolationException("fk_material_categoria")).when(repository).flush();
		var exception = assertThrows(ResponseStatusException.class, () -> service.excluir(id));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Categoria possui materiais vinculados e nao pode ser excluida.", exception.getReason());
		verify(repository).delete(categoria);
		verify(repository).flush();
		verify(materiais, never()).delete(any(Material.class));
	}

	@Test
	void naoTentaExcluirQuandoHaQualquerMaterialVinculado() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Categoria("Categoria", null)));
		when(materiais.existsByCategoriaId(id)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.excluir(id)).getStatusCode());
		verify(repository, never()).delete(any());
		verify(repository, never()).flush();
	}

	@Test
	void listagemPreservaPaginacaoEMapeiaParaDto() {
		var pageable = PageRequest.of(0, 20, Sort.by("nome", "id"));
		when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(new Categoria("Categoria", "Descricao")), pageable, 1));
		var resultado = service.listar(pageable);
		assertEquals(1, resultado.getTotalElements());
		assertEquals(pageable, resultado.getPageable());
		assertEquals("Categoria", resultado.getContent().getFirst().nome());
		assertEquals("Descricao", resultado.getContent().getFirst().descricao());
		verifyNoInteractions(materiais);
	}

	@Test
	void dtoNormalizaEspacosESemDescricaoFicaNulo() {
		assertEquals(new CategoriaRequest("Categoria", "Descricao"), new CategoriaRequest(" Categoria ", " Descricao "));
		assertNull(new CategoriaRequest("Categoria", " ").descricao());
		assertNull(new CategoriaRequest("Categoria", null).descricao());
	}
}

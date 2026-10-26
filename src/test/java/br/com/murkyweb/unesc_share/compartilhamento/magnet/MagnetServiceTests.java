package br.com.murkyweb.unesc_share.compartilhamento.magnet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetParser;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetService;
import br.com.murkyweb.unesc_share.compartilhamento.service.InfoHashGuard;

class MagnetServiceTests {
	private final MagnetRepository repository = mock(MagnetRepository.class);
	private final InfoHashGuard hashes = mock(InfoHashGuard.class);
	private final MagnetService service = new MagnetService(new MagnetParser(), repository, hashes);
	private static final String URI = "magnet:?xt=urn:btih:" + "0".repeat(40);

	@Test
	void exigeDeUmADezMagnets() {
		assertThrows(ResponseStatusException.class, () -> service.preparar(null));
		assertThrows(ResponseStatusException.class, () -> service.preparar(List.of()));
		assertThrows(ResponseStatusException.class, () -> service.preparar(Collections.nCopies(11, URI)));
		verifyNoInteractions(repository);
	}

	@Test
	void duplicidadeEntreHexEBase32NaMesmaRequisicaoRetorna409() {
		var exception = assertThrows(ResponseStatusException.class, () -> service.preparar(List.of(URI, "magnet:?xt=urn:btih:" + "A".repeat(32))));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		verify(repository, never()).saveAllAndFlush(any());
	}

	@Test
	void hashJaCadastradoRetorna409() {
		when(repository.existsByInfoHashIgnoreCase("0".repeat(40))).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.preparar(List.of(URI))).getStatusCode());
	}

	@Test
	void conflitoDeConstraintNaAssociacaoNaoExpoeSql() {
		var validados = service.preparar(List.of(URI));
		when(repository.saveAllAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL interno sensivel"));
		var exception = assertThrows(ResponseStatusException.class, () -> service.associar(null, validados));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertNull(exception.getCause());
	}

	@Test void preparacaoUsaMaterialDeDestinoOuNullParaCadastroNovo() {
		var id = java.util.UUID.randomUUID();
		service.preparar(List.of(URI), id);
		verify(hashes).bloquear(List.of("0".repeat(40)));
		verify(hashes).verificarMaterial("0".repeat(40), id);
		service.preparar(List.of(URI));
		verify(hashes).verificarMaterial("0".repeat(40), null);
	}
}

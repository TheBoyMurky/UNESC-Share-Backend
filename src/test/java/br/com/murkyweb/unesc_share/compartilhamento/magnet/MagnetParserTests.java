package br.com.murkyweb.unesc_share.compartilhamento.magnet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetParser;

class MagnetParserTests {
	private final MagnetParser parser = new MagnetParser();
	private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

	@Test
	void normalizaV1HexEEsquemaEPreservaParametrosSemConsultasExternas() {
		var magnet = parser.parse("  MAGNET:?dn=Nome%20Teste&xt=urn:btih:" + HASH.toUpperCase()
				+ "&tr=http%3A%2F%2F127.0.0.1%3A9%2Fannounce&tr=udp%3A%2F%2Ftracker.invalid%3A80  ");
		assertEquals(HASH, magnet.infoHash());
		assertEquals("magnet:?xt=urn:btih:" + HASH + "&dn=Nome%20Teste&tr=http%3A%2F%2F127.0.0.1%3A9%2Fannounce&tr=udp%3A%2F%2Ftracker.invalid%3A80", magnet.magnetUri());
	}

	@Test
	void converteBase32NaoTrivialEZerosEUnsParaHex() {
		assertEquals("000102030405060708090a0b0c0d0e0f10111213", parser.parse("magnet:?xt=urn:btih:AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQT").infoHash());
		assertEquals("0".repeat(40), parser.parse("magnet:?xt=urn:btih:" + "a".repeat(32)).infoHash());
		assertEquals("f".repeat(40), parser.parse("magnet:?xt=urn:btih:" + "7".repeat(32)).infoHash());
	}

	@Test
	void aceitaV2Sha256MultihashERetornaHashCompleto() {
		var magnet = parser.parse("magnet:?xt=urn:btmh:1220" + "AB".repeat(32));
		assertEquals("ab".repeat(32), magnet.infoHash());
		assertEquals("magnet:?xt=urn:btmh:1220" + "ab".repeat(32), magnet.magnetUri());
	}

	@Test
	void aceitaXtCodificadoSemDuplaDecodificacao() {
		assertEquals(HASH, parser.parse("magnet:?%78%74=urn%3Abtih%3A" + HASH).infoHash());
		assertThrows(ResponseStatusException.class, () -> parser.parse("magnet:?xt=urn%253Abtih%253A" + HASH));
	}

	@Test
	void representacoesTextuaisNaoExpoemUriComCredenciaisDeTracker() {
		var magnet = parser.parse("magnet:?xt=urn:btih:" + HASH + "&tr=https%3A%2F%2Ftracker.invalid%2Fchave-privada");
		assertFalse(magnet.toString().contains("chave-privada"));
		assertFalse(new br.com.murkyweb.unesc_share.material.dto.MaterialCadastroRequest("T", "D", null, null,
				java.util.List.of(magnet.magnetUri())).toString().contains("chave-privada"));
		assertFalse(new br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetResponse(null, magnet.magnetUri(), HASH, null)
				.toString().contains("chave-privada"));
	}

	@ParameterizedTest
	@MethodSource("invalidos")
	void rejeitaUrisInvalidasOuTopicosNaoSuportadosSemExporValor(String uri) {
		var exception = assertThrows(ResponseStatusException.class, () -> parser.parse(uri));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertNull(exception.getCause());
		assertEquals("Magnet URI invalido ou nao suportado. Informe um unico xt btih v1 ou btmh SHA-256 v2.", exception.getReason());
	}

	static Stream<String> invalidos() {
		String valido = "magnet:?xt=urn:btih:" + HASH;
		return Stream.of(null, "", " ", "https://example.com/arquivo.pdf", "javascript:alert(1)", "magnet://host?xt=urn:btih:" + HASH,
				"magnet:/?xt=urn:btih:" + HASH, "magnet:?", "magnet:?dn=SemHash", "magnet:?xt=urn:btih:curto",
				"magnet:?xt=urn:btih:" + "g".repeat(40), "magnet:?xt=urn:btih:" + "0".repeat(32),
				"magnet:?xt=urn:btmh:1220" + "a".repeat(63), "magnet:?xt=urn:btmh:1320" + "a".repeat(64),
				valido + "#fragmento", valido + "&xt=urn:btih:" + HASH, valido + "&xt=urn:btmh:1220" + "a".repeat(64),
				valido + "&dn=Nome nao codificado", valido + "&dn=%0Asegredo", valido + "&dn=%00", valido + "&dn=%ZZ",
				valido + "&", valido + "&sem-igual", valido + "&=vazio", valido + "&dn=" + "a".repeat(4000),
				valido + "&tr=x".repeat(64), valido + "&dn=\ud800");
	}
}

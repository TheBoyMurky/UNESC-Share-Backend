package br.com.murkyweb.unesc_share.compartilhamento.torrent;

import static br.com.murkyweb.unesc_share.compartilhamento.torrent.TorrentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentParser;

class TorrentParserTests {
	private final TorrentParser parser = new TorrentParser();

	@Test void hashVemDosBytesOriginaisDeInfoNaoDoArquivoInteiro() {
		assertEquals(hash("teste.pdf"), parser.infoHash(torrent("teste.pdf")));
		assertEquals(hash("teste.pdf"), parser.infoHash(encode(Map.of("announce", "http://127.0.0.1:1/nunca-acessar",
				"comment", "comentario diferente", "info", info("teste.pdf")))));
	}
	@Test void aceitaBinarioEmPiecesEMultiplosArquivos() {
		var info = Map.of("files", List.of(Map.of("length", 2L, "path", List.of("pasta", "a.pdf")),
				Map.of("length", 3L, "path", List.of("b.epub"))), "name", "Colecao", "piece length", 4L, "pieces", new byte[40]);
		assertEquals(40, parser.infoHash(encode(Map.of("info", info))).length());
	}
	@Test void aceitaArquivoVazioSemPieces() {
		var info = Map.of("length", 0L, "name", "vazio", "piece length", 16384L, "pieces", new byte[0]);
		assertNotNull(parser.infoHash(encode(Map.of("info", info))));
	}
	@ParameterizedTest @MethodSource("invalidos")
	void rejeitaMetainfoMalformadoOuNaoSuportadoSemRevelarConteudo(byte[] input) {
		var exception = assertThrows(ResponseStatusException.class, () -> parser.infoHash(input));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertNull(exception.getCause());
	}
	static Stream<byte[]> invalidos() {
		var changed = new java.util.ArrayList<byte[]>();
		for (var change : List.<Map<String, Object>>of(
				Map.of("length", -1L), Map.of("length", "1"), Map.of("piece length", 0L), Map.of("piece length", -1L),
				Map.of("pieces", new byte[21]), Map.of("pieces", new byte[0]), Map.of("pieces", new byte[40]),
				Map.of("name", "../arquivo"), Map.of("name", ".."), Map.of("name", "C:\\arquivo"),
				Map.of("name", new byte[]{(byte) 0xff}), Map.of("name", "a\u0000b"), Map.of("name", " "),
				Map.of("meta version", 2L), Map.of("file tree", Map.of()), Map.of("files", List.of()))) {
			var value = new HashMap<>(info("a.pdf")); value.putAll(change); changed.add(encode(Map.of("info", value)));
		}
		for (String field : List.of("length", "name", "piece length", "pieces")) {
			var value = new HashMap<>(info("a.pdf")); value.remove(field); changed.add(encode(Map.of("info", value)));
		}
		for (var files : List.of(
				List.of(Map.of("length", 1L, "path", List.of())),
				List.of(Map.of("length", 1L, "path", List.of("..", "x"))),
				List.of(Map.of("length", 1L, "path", List.of("x")), Map.of("length", 0L, "path", List.of("x"))),
				List.of(Map.of("length", 1L, "path", List.of("x")), Map.of("length", 0L, "path", List.of("x", "y"))),
				List.of(Map.of("length", Long.MAX_VALUE, "path", List.of("x")), Map.of("length", 1L, "path", List.of("y"))))) {
			changed.add(encode(Map.of("info", Map.of("files", files, "name", "diretorio", "piece length", 16384L, "pieces", new byte[20]))));
		}
		return Stream.concat(changed.stream(), Stream.of(null, new byte[0], new byte[TorrentParser.MAX_BYTES + 1],
				bytes("%PDF-1.7"), bytes("PK\u0003\u0004"), bytes("de"), bytes("li1ee"), bytes("d4:infoi1ee"),
				bytes("d4:infode4:infodee"), bytes("d1:bi0e1:ai0ee"), bytes("d1:ai01ee"), bytes("d1:ai-0ee"),
				bytes("d1:ai9223372036854775808ee"), bytes("d01:ai0ee"), bytes("d1:a9999999:x"), bytes("d1:a2:x"),
				bytes("d1:ai0e"), bytes("deextra"), bytes("l".repeat(34) + "e".repeat(34)),
				bytes("l" + "0:".repeat(20_001) + "e"), bytes("d1:aiee"), bytes("d1:ai+1ee")));
	}
}

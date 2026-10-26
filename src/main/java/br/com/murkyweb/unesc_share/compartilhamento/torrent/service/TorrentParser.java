package br.com.murkyweb.unesc_share.compartilhamento.torrent.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Le apenas metainfo v1, com limites de recursos; nunca abre caminhos ou URLs do torrent. */
@Component
public class TorrentParser {

	public static final int MAX_BYTES = 2 * 1024 * 1024;

	public String infoHash(byte[] bytes) {
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) { throw invalido(); }
		var reader = new Reader(bytes);
		var root = dictionary(reader.read(0));
		if (reader.position != bytes.length) { throw invalido(); }
		var infoNode = root.get(key("info"));
		var info = dictionary(infoNode);
		// Nao calcular um hash v1 de um metainfo v2/hibrido como se fosse suportado.
		if (info.containsKey(key("meta version")) || info.containsKey(key("file tree"))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Torrents v2 e hibridos ainda nao sao suportados.");
		}
		segment(string(info.get(key("name"))));
		long pieceLength = number(info.get(key("piece length")));
		if (pieceLength <= 0) { throw invalido(); }
		byte[] pieces = string(info.get(key("pieces")));
		boolean single = info.containsKey(key("length")), multi = info.containsKey(key("files"));
		if (single == multi) { throw invalido(); }
		long total = 0;
		if (single) {
			total = length(info.get(key("length")));
		} else {
			var files = list(info.get(key("files")));
			if (files.isEmpty() || files.size() > 10_000) { throw invalido(); }
			var paths = new HashSet<List<String>>();
			for (var file : files) {
				var entry = dictionary(file);
				try { total = Math.addExact(total, length(entry.get(key("length")))); }
				catch (ArithmeticException exception) { throw invalido(); }
				var path = list(entry.get(key("path")));
				if (path.isEmpty() || path.size() > 32) { throw invalido(); }
				var parts = path.stream().map(node -> segment(string(node))).toList();
				if (!paths.add(parts)) { throw invalido(); }
			}
			// Evita um arquivo que tambem seja diretorio de outro arquivo.
			for (var path : paths) {
				for (int i = 1; i < path.size(); i++) {
					if (paths.contains(path.subList(0, i))) { throw invalido(); }
				}
			}
		}
		long expectedPieces = total / pieceLength + (total % pieceLength == 0 ? 0 : 1);
		if (pieces.length % 20 != 0 || pieces.length / 20 != expectedPieces) { throw invalido(); }
		try {
			var digest = MessageDigest.getInstance("SHA-1");
			// Hash dos bytes originais de info, nao de uma reserializacao.
			digest.update(bytes, infoNode.start(), infoNode.end() - infoNode.start());
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-1 indisponivel."); }
	}

	private static ByteBuffer key(String key) { return ByteBuffer.wrap(key.getBytes(StandardCharsets.US_ASCII)); }
	@SuppressWarnings("unchecked")
	private static Map<ByteBuffer, Node> dictionary(Node node) {
		if (node == null || !(node.value() instanceof Map)) { throw invalido(); }
		return (Map<ByteBuffer, Node>) node.value();
	}
	@SuppressWarnings("unchecked")
	private static List<Node> list(Node node) {
		if (node == null || !(node.value() instanceof List)) { throw invalido(); }
		return (List<Node>) node.value();
	}
	private static byte[] string(Node node) {
		if (node == null || !(node.value() instanceof byte[] value)) { throw invalido(); }
		return value;
	}
	private static long number(Node node) {
		if (node == null || !(node.value() instanceof Long value)) { throw invalido(); }
		return value;
	}
	private static long length(Node node) {
		long value = number(node);
		if (value < 0) { throw invalido(); }
		return value;
	}
	private static String segment(byte[] bytes) {
		String value;
		try {
			value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException exception) { throw invalido(); }
		if (value.isBlank() || value.equals(".") || value.equals("..") || value.length() > 255
				|| value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\' || c == ':')) { throw invalido(); }
		return value;
	}
	private static ResponseStatusException invalido() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo .torrent v1 invalido ou fora dos limites suportados.");
	}
	private record Node(Object value, int start, int end) { }

	private static final class Reader {
		private final byte[] bytes;
		private int position;
		private int nodes;
		private Reader(byte[] bytes) { this.bytes = bytes; }

		private Node read(int depth) {
			if (depth > 32 || ++nodes > 20_000 || position >= bytes.length) { throw invalido(); }
			int start = position;
			int token = bytes[position] & 0xff;
			Object value;
			if (token == 'i') {
				position++;
				int numberStart = position;
				while (position < bytes.length && bytes[position] != 'e') { position++; }
				if (position >= bytes.length || position - numberStart > 20) { throw invalido(); }
				String digits = new String(bytes, numberStart, position - numberStart, StandardCharsets.US_ASCII);
				if (!digits.matches("0|-?[1-9][0-9]*")) { throw invalido(); }
				try { value = Long.parseLong(digits); }
				catch (NumberFormatException exception) { throw invalido(); }
				position++;
			} else if (token == 'l') {
				position++;
				var items = new ArrayList<Node>();
				while (!end()) { items.add(read(depth + 1)); }
				position++;
				value = items;
			} else if (token == 'd') {
				position++;
				var items = new LinkedHashMap<ByteBuffer, Node>();
				byte[] previous = null;
				while (!end()) {
					byte[] name = string(read(depth + 1));
					if (name.length > 256 || previous != null && Arrays.compareUnsigned(previous, name) >= 0) { throw invalido(); }
					items.put(ByteBuffer.wrap(name), read(depth + 1));
					previous = name;
				}
				position++;
				value = items;
			} else if (token >= '0' && token <= '9') {
				int digitsStart = position;
				long size = 0;
				while (position < bytes.length && bytes[position] >= '0' && bytes[position] <= '9') {
					if (position - digitsStart >= 7) { throw invalido(); }
					size = size * 10 + bytes[position++] - '0';
				}
				if (position - digitsStart > 1 && bytes[digitsStart] == '0' || position >= bytes.length || bytes[position++] != ':'
						|| size > bytes.length - position) { throw invalido(); }
				value = Arrays.copyOfRange(bytes, position, position + (int) size);
				position += (int) size;
			} else { throw invalido(); }
			return new Node(value, start, position);
		}
		private boolean end() {
			if (position >= bytes.length) { throw invalido(); }
			return bytes[position] == 'e';
		}
	}
}

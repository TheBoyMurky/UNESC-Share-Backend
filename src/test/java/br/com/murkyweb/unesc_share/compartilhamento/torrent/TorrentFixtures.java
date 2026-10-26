package br.com.murkyweb.unesc_share.compartilhamento.torrent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Metainfo sintetico, sem conteudo academico ou consulta externa. */
public final class TorrentFixtures {
	private TorrentFixtures() { }
	public static Map<String, Object> info(String name) {
		return Map.of("length", 1L, "name", name, "piece length", 16384L, "pieces", new byte[20]);
	}
	public static byte[] torrent(String name) { return encode(Map.of("info", info(name))); }
	public static String hash(String name) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(encode(info(name)))); }
		catch (Exception exception) { throw new IllegalStateException(exception); }
	}
	public static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
	public static byte[] encode(Object value) {
		var output = new ByteArrayOutputStream();
		if (value instanceof Map<?, ?> map) {
			output.write('d');
			var sorted = new TreeMap<String, Object>();
			map.forEach((key, item) -> sorted.put((String) key, item));
			sorted.forEach((key, item) -> { output.writeBytes(encode(key)); output.writeBytes(encode(item)); });
			output.write('e');
		} else if (value instanceof List<?> list) {
			output.write('l'); list.forEach(item -> output.writeBytes(encode(item))); output.write('e');
		} else if (value instanceof Number number) {
			output.writeBytes(bytes("i" + number.longValue() + "e"));
		} else {
			byte[] data = value instanceof byte[] b ? b : bytes((String) value);
			output.writeBytes(bytes(data.length + ":")); output.writeBytes(data);
		}
		return output.toByteArray();
	}
}

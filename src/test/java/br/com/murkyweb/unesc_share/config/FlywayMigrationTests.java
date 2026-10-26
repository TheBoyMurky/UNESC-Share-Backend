package br.com.murkyweb.unesc_share.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class FlywayMigrationTests {

	@Test
	void migracaoInicialCriaTodasAsTabelasEmUmSchemaTemporario() throws Exception {
		String schema = "flyway_verify_" + UUID.randomUUID().toString().replace("-", "");
		var dataSource = new PGSimpleDataSource();
		dataSource.setServerName(env("DB_HOST", "localhost"));
		dataSource.setPortNumber(Integer.parseInt(env("POSTGRES_PORT", "5432")));
		dataSource.setDatabaseName(env("POSTGRES_DB", "unescshare"));
		dataSource.setUser(env("POSTGRES_USER", "unescshare"));
		dataSource.setPassword(env("POSTGRES_PASSWORD", ""));
		dataSource.setCurrentSchema(schema);

		var flyway = Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.load();

		try {
			flyway.migrate();
			assertNotNull(flyway.info().current());
			assertEquals("1", flyway.info().current().getVersion().getVersion());
			try (Connection connection = dataSource.getConnection();
					PreparedStatement statement = connection.prepareStatement("""
							select count(*) from information_schema.tables
							where table_schema = ? and table_type = 'BASE TABLE'
								and table_name <> 'flyway_schema_history'
							""")) {
				statement.setString(1, schema);
				try (var result = statement.executeQuery()) {
						result.next();
						assertEquals(13, result.getInt(1));
					}
			}
		} finally {
			try {
				flyway.clean();
			} finally {
				try (Connection connection = DriverManager.getConnection(
						"jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("POSTGRES_PORT", "5432")
								+ "/" + env("POSTGRES_DB", "unescshare"),
						env("POSTGRES_USER", "unescshare"), env("POSTGRES_PASSWORD", ""));
						var statement = connection.createStatement()) {
					statement.execute("drop schema if exists " + schema + " cascade");
				}
			}
		}
	}

	private String env(String key, String fallback) {
		String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}
}

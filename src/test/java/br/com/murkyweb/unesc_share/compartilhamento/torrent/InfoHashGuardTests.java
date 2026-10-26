package br.com.murkyweb.unesc_share.compartilhamento.torrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.murkyweb.unesc_share.compartilhamento.service.InfoHashGuard;

@SpringBootTest
@ActiveProfiles("test")
class InfoHashGuardTests {
	@Autowired InfoHashGuard guard;
	@Autowired PlatformTransactionManager manager;

	@Test void lockTransacionalCoordenaConexoesEELiberadoNoRollback() throws Exception {
		String hash = UUID.randomUUID().toString().replace("-", "") + "01234567";
		var firstLocked = new CountDownLatch(1); var secondStarted = new CountDownLatch(1);
		var release = new CountDownLatch(1); var secondLocked = new CountDownLatch(1);
		var tx = new TransactionTemplate(manager); tx.setTimeout(10);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var first = pool.submit(() -> tx.executeWithoutResult(status -> {
				guard.bloquear(List.of(hash)); firstLocked.countDown();
				try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Timeout no teste."); } }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
				status.setRollbackOnly();
			}));
			assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
			var second = pool.submit(() -> tx.executeWithoutResult(status -> {
				secondStarted.countDown(); guard.bloquear(List.of(hash)); secondLocked.countDown(); status.setRollbackOnly();
			}));
			try {
				assertTrue(secondStarted.await(5, TimeUnit.SECONDS)); assertFalse(secondLocked.await(200, TimeUnit.MILLISECONDS));
			} finally { release.countDown(); }
			first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS); assertEquals(0, secondLocked.getCount());
		}
	}
}

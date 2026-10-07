package com.dec.lite.query;

import com.dec.lite.action.*;
import com.dec.lite.information.ModelContext;
import com.dec.lite.session.TransactionPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCatalogTest {
    @Test void failedReloadKeepsPublishedSnapshotAndExistingSession() throws Exception {
        Path yaml = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        RuntimeCatalog catalog = catalog(yaml);
        RuntimeSnapshot original = catalog.current();
        try (var oldSession = original.openSession(new ModelContext(Map.of("status", 1L)), Map.of(), Instant.MAX, null, TransactionPolicy.NONE)) {
            assertEquals(original.digest(), oldSession.contextVersion());
            Path changed = Files.createTempDirectory("dec-p8-reload-");
            try (var files = Files.list(yaml)) {
                for (Path file : files.toList()) Files.copy(file, changed.resolve(file.getFileName()));
            }
            Path systems = changed.resolve("systems-order.yaml");
            Files.writeString(systems, Files.readString(systems).replace("status = 1", "status = 9"));
            RuntimeSnapshot replacement = catalog.reload(changed);
            assertNotEquals(original.digest(), replacement.digest());
            assertEquals(original.digest(), oldSession.contextVersion());
            try (var newSession = replacement.openSession(new ModelContext(Map.of("status", 1L)), Map.of(), Instant.MAX, null, TransactionPolicy.NONE)) {
                assertEquals(replacement.digest(), newSession.contextVersion());
            }
            Files.writeString(changed.resolve("business-order-payment.yaml"), "kind: business\nversion: dec/v1\nbusiness: {}\n");
            assertThrows(RuntimeException.class, () -> catalog.reload(changed));
            assertSame(replacement, catalog.current());
            assertThrows(IllegalStateException.class,
                    () -> replacement.ruleViews().register("x", "v", "r", ignored -> null));
            assertThrows(IllegalStateException.class,
                    () -> replacement.project().addGap(null));
        }
    }
    @Test void readersObserveOnlyCompletePublishedSnapshots() throws Exception {
        Path yaml = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        Path xml = Path.of(getClass().getClassLoader().getResource("mix-xml").toURI());
        RuntimeCatalog catalog = catalog(yaml);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var reader = pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    RuntimeSnapshot snapshot = catalog.current();
                    assertEquals(5, snapshot.directories().directories().size());
                    assertEquals(16, snapshot.information().definitions().size());
                    assertNotNull(snapshot.queries().compile(DirectoryQuery.find("PayResult")));
                }
            });
            var writer = pool.submit(() -> { for (int i = 0; i < 4; i++) catalog.reload(xml); });
            reader.get(); writer.get();
            assertEquals(catalog.current().digest(), catalog.reload(yaml).digest());
        }
    }
    private static RuntimeCatalog catalog(Path input) {
        return new RuntimeCatalog(input, project -> new RuleViewCompiler().register(project, null), project ->
                new CustomActionRegistry().register(new CustomAction() {
                    public String name() { return "smsNotify"; }
                    public void execute(ActionExecutionContext context) { }
                }));
    }
}

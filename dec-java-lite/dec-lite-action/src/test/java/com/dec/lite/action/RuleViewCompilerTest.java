package com.dec.lite.action;

import com.dec.lite.information.ModelContext;
import com.dec.lite.information.MutationSet;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleViewCompilerTest {
    @Test
    void compilesMixRuleViewsWithSystemOwnershipAndRunsChecksAndDsl() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        RuleViewCompiler compiler = new RuleViewCompiler();
        Map<?, ?> compiled = compiler.compile(project);
        assertEquals(14, compiled.size());

        RuleViewRegistry registry = compiler.register(project, null);
        ModelContext model = model(1L, "UNKNOWN");
        RecognitionResult waitPay = registry.invoke("order", "isWaitPay", model, Map.of(), new MutationSet());
        assertEquals(RecognitionResult.Status.TRUE, waitPay.status());

        MutationSet mutations = new MutationSet();
        RecognitionResult success = registry.invoke("order", "confirmPaySuccess", model, Map.of(), mutations);
        assertEquals(RecognitionResult.Status.TRUE, success.status());
        assertEquals(3L, model.read("status"));
        assertEquals(3, mutations.mutations().size());
    }

    @Test
    void externalDataOperationsFailClosedWithoutAdapter() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        RuleViewRegistry registry = new RuleViewCompiler().register(project, null);
        RecognitionResult result = registry.invoke("payment", "pay", model(0L, "UNKNOWN"), Map.of(), new MutationSet());
        assertEquals(RecognitionResult.Status.ERROR, result.status());
        assertTrue(result.errors().get(0).contains("DataOperationAdapter"));
    }

    private static ModelContext model(Long status, String resultCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status);
        values.put("orderDetailList", List.of(new LinkedHashMap<>(Map.of("status", status)), new LinkedHashMap<>(Map.of("status", status))));
        values.put("payInfo", new LinkedHashMap<>(Map.of("resultCode", resultCode)));
        return new ModelContext(values);
    }
    private static Path resourceDirectory() throws URISyntaxException {
        return Path.of(RuleViewCompilerTest.class.getClassLoader().getResource("mix").toURI());
    }
}

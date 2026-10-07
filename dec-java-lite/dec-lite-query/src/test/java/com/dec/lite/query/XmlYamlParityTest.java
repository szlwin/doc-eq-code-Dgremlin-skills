package com.dec.lite.query;

import com.dec.lite.action.BusinessActionParser;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.InformationParser;
import com.dec.lite.model.DecProject;
import com.dec.lite.model.SemanticDigest;
import com.dec.lite.parser.DecXmlParser;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XmlYamlParityTest {
    @Test void apiAndEnumHaveTheSameCanonicalContract() throws Exception {
        DecProject yaml = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("aux-yaml").toURI()));
        DecProject xml = new DecXmlParser().parse(Path.of(getClass().getClassLoader().getResource("aux-xml").toURI()));
        assertEquals(yaml.getDesignIds(), xml.getDesignIds());
        assertEquals(SemanticDigest.project(yaml), SemanticDigest.project(xml));
    }
    @Test void fullMixHasTheSameCanonicalAndCompiledContract() throws Exception {
        Path yamlPath = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        Path xmlPath = Path.of(getClass().getClassLoader().getResource("mix-xml").toURI());
        DecProject yaml = new DecYamlParser().parse(yamlPath);
        DecProject xml = new DecXmlParser().parse(xmlPath);
        assertTrue(yaml.getDesignGaps().isEmpty());
        assertTrue(xml.getDesignGaps().isEmpty());
        assertEquals(yaml.getDesignIds(), xml.getDesignIds());
        assertEquals(SemanticDigest.project(yaml), SemanticDigest.project(xml));
        var yamlInfo = new InformationParser().parse(yaml);
        var xmlInfo = new InformationParser().parse(xml);
        assertEquals(yamlInfo.definitions().keySet(), xmlInfo.definitions().keySet());
        assertEquals(yamlInfo.graphDigest(), xmlInfo.graphDigest());
        assertEquals(new BusinessActionParser().parse(yaml).stream().map(a -> a.id() + ":" + a.ruleRef()).toList(),
                new BusinessActionParser().parse(xml).stream().map(a -> a.id() + ":" + a.ruleRef()).toList());
        assertEquals(new RuleViewCompiler().compile(yaml).keySet(), new RuleViewCompiler().compile(xml).keySet());
        var yamlGraph = new DirectoryGraphCompiler().compile(yaml, yamlInfo);
        var xmlGraph = new DirectoryGraphCompiler().compile(xml, xmlInfo);
        assertEquals(yamlGraph.executionEdges(), xmlGraph.executionEdges());
        assertEquals(yamlGraph.caseEdges(), xmlGraph.caseEdges());
        assertEquals(yamlGraph.backEdges().stream().map(e -> e.from() + ":" + e.to()).toList(),
                xmlGraph.backEdges().stream().map(e -> e.from() + ":" + e.to()).toList());
        QueryPlan yamlQuery = new QueryCompiler(yaml, yamlGraph, yamlInfo).compile(DirectoryQuery.find("PayResult").with("orderDetailList"));
        QueryPlan xmlQuery = new QueryCompiler(xml, xmlGraph, xmlInfo).compile(DirectoryQuery.find("PayResult").with("orderDetailList"));
        assertEquals(yamlQuery.route(), xmlQuery.route());
        assertEquals(yamlQuery.joins(), xmlQuery.joins());
        assertEquals(yamlQuery.predicate(), xmlQuery.predicate());
        assertEquals(yamlQuery.selectedCases(), xmlQuery.selectedCases());
    }
}

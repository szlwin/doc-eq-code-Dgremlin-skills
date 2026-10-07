package com.dec.lite.cli;

import com.dec.lite.model.DecDocument;
import com.dec.lite.model.DecProject;
import com.dec.lite.model.DesignGap;
import com.dec.lite.generator.GenerationException;
import com.dec.lite.generator.GeneratorPipeline;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationCompilationException;
import com.dec.lite.information.InformationParser;
import com.dec.lite.action.BusinessActionParser;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.action.ActionExecutionException;
import com.dec.lite.directory.DirectoryCompilationException;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.directory.PathPlanner;
import com.dec.lite.directory.DirectoryExecutionException;
import com.dec.lite.query.DirectoryQuery;
import com.dec.lite.query.QueryCompiler;
import com.dec.lite.query.QueryCompilationException;
import com.dec.lite.query.SqlTranslator;
import com.dec.lite.query.MySqlDialect;
import com.dec.lite.parser.DecParseException;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.parser.DecXmlParser;
import com.dec.lite.model.SemanticDigest;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            printUsage();
            return args.length == 0 ? 2 : 0;
        }
        String command = args[0];
        if (!"inspect".equals(command) && !"generate".equals(command) && !"information".equals(command) && !"action".equals(command) && !"directory".equals(command) && !"query".equals(command)) {
            System.err.println("ERROR unsupported command: " + command);
            printUsage();
            return 2;
        }
        Path yaml = optionPath(args, "--yaml");
        Path xml = optionPath(args, "--xml");
        if ((yaml == null) == (xml == null)) {
            System.err.println("ERROR specify exactly one of --yaml or --xml <file-or-directory>");
            return 2;
        }
        try {
            DecProject project = yaml != null ? new DecYamlParser().parse(yaml) : new DecXmlParser().parse(xml);
            printSummary(project);
            if (!project.getDesignGaps().isEmpty()) {
                for (DesignGap gap : project.getDesignGaps()) System.err.println("DESIGN_GAP " + gap);
                return 3;
            }
            if ("information".equals(command)) {
                InformationCompilation compilation = new InformationParser().parse(project);
                printInformation(compilation);
                return 0;
            }
            if ("action".equals(command)) {
                System.out.println("ruleViews=" + new RuleViewCompiler().compile(project).size());
                System.out.println("actions=" + new BusinessActionParser().parse(project).size());
                return 0;
            }
            if ("directory".equals(command)) {
                var graph = new DirectoryGraphCompiler().compile(project, new InformationParser().parse(project));
                System.out.println("directoryRoot=" + graph.root());
                System.out.println("directories=" + graph.directories().size());
                System.out.println("executionEdges=" + graph.executionEdges().size());
                System.out.println("caseEdges=" + graph.caseEdges().size());
                System.out.println("backEdges=" + graph.backEdges().size());
                String target = optionValue(args, "--target", null);
                if (target != null) System.out.println("path=" + new PathPlanner().plan(graph, null, target).directories());
                return 0;
            }
            if ("query".equals(command)) {
                String directory = optionValue(args, "--directory", null);
                if (directory == null) throw new IllegalArgumentException("--directory is required for query");
                var information = new InformationParser().parse(project);
                var graph = new DirectoryGraphCompiler().compile(project, information);
                DirectoryQuery query = DirectoryQuery.find(directory);
                String selectedCase = optionValue(args, "--eq", null);
                if (selectedCase != null) query = query.eq(selectedCase);
                String from = optionValue(args, "--from", null);
                if (from != null) query = query.from(from);
                String to = optionValue(args, "--to", null);
                if (to != null) query = query.to(to);
                for (String relation : optionValues(args, "--with")) query = query.with(relation);
                for (String filter : optionValues(args, "--where")) {
                    int split = filter.indexOf('=');
                    if (split < 1) throw new IllegalArgumentException("--where requires property=value");
                    query = query.where(filter.substring(0, split), filter.substring(split + 1));
                }
                query = query.page(Integer.parseInt(optionValue(args, "--offset", "0")), Integer.parseInt(optionValue(args, "--size", "50")))
                        .candidateLimit(Integer.parseInt(optionValue(args, "--candidate-limit", "1000")));
                var plan = new QueryCompiler(project, graph, information).compile(query);
                System.out.println(new SqlTranslator(new MySqlDialect()).explain(plan));
                return 0;
            }
            if ("generate".equals(command)) {
                Path output = optionPath(args, "--output");
                if (output == null) {
                    System.err.println("ERROR --output <directory> is required for generate");
                    return 2;
                }
                String basePackage = optionValue(args, "--base-package", "com.dec.generated");
                GeneratorPipeline.GenerationResult result = new GeneratorPipeline()
                        .generate(project, output, basePackage);
                System.out.println("generated=" + result.files().size());
                for (String file : result.files()) System.out.println("file=" + file);
            }
            return 0;
        } catch (DecParseException | GenerationException | InformationCompilationException | ActionExecutionException | DirectoryCompilationException | DirectoryExecutionException | QueryCompilationException | IllegalArgumentException e) {
            System.err.println("ERROR " + e.getMessage());
            return 1;
        }
    }

    private static Path optionPath(String[] args, String option) {
        for (int i = 1; i < args.length - 1; i++) {
            if (option.equals(args[i])) return Path.of(args[i + 1]);
        }
        return null;
    }

    private static String optionValue(String[] args, String option, String fallback) {
        for (int i = 1; i < args.length - 1; i++) {
            if (option.equals(args[i])) return args[i + 1];
        }
        return fallback;
    }
    private static java.util.List<String> optionValues(String[] args, String option) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (int i = 1; i < args.length - 1; i++) if (option.equals(args[i])) result.add(args[i + 1]);
        return result;
    }

    private static void printSummary(DecProject project) {
        System.out.println("documents=" + project.getDocuments().size());
        for (DecDocument document : project.getDocuments()) {
            System.out.println("document kind=" + document.kind().value()
                    + " version=" + document.version()
                    + " source=" + document.source()
                    + " nodes=" + document.nodes().size());
        }
        System.out.println("designIds=" + project.getDesignIds().size());
        for (String designId : project.getDesignIds()) System.out.println("designId=" + designId);
        System.out.println("designGaps=" + project.getDesignGaps().size());
        System.out.println("semanticDigest=" + SemanticDigest.project(project));
    }

    private static void printUsage() {
        System.out.println("Usage: dec-lite <command> (--yaml <canonical-yaml> | --xml <dec-xml>) [options]");
        System.out.println("       commands: inspect, information, action, directory, query, generate");
        System.out.println("       directory options: [--target <directory>]");
        System.out.println("       query options: --directory <name> [--eq <case>] [--from <directory>] [--to <directory-or-case>] [--with <relation>] [--where property=value] [--offset N] [--size N] [--candidate-limit N]");
        System.out.println("       generate options: --output <directory> [--base-package <package>]");
    }

    private static void printInformation(InformationCompilation compilation) {
        System.out.println("information=" + compilation.definitions().size());
        System.out.println("graphDigest=" + compilation.graphDigest());
        for (var key : compilation.topologicalOrder()) {
            var definition = compilation.definitions().get(key);
            System.out.println("information key=" + key + " kind=" + definition.kind()
                    + " dependencies=" + compilation.dependencies().getOrDefault(key, java.util.Set.of()));
        }
    }
}

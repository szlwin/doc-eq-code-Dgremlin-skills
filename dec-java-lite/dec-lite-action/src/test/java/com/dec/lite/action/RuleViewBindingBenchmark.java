package com.dec.lite.action;

import com.dec.lite.parser.DecYamlParser;
import java.nio.file.Path;
import java.util.Arrays;

/** Optional assembly-only measurement. No CI timing threshold and no database/endpoint claims. */
public final class RuleViewBindingBenchmark {
    private static volatile Object sink;

    public static void main(String[] args) throws Exception {
        var project = new DecYamlParser().parse(Path.of(args[0]));
        var compiler = new RuleViewCompiler();
        var compiled = compiler.compile(project);
        Runnable old = () -> sink = compiler.register(project, null).freeze();
        Runnable bound = () -> sink = compiler.bind(compiled, null).freeze();
        for (int i = 0; i < 2_000; i++) { old.run(); bound.run(); }
        double[] legacy = new double[7], reused = new double[7];
        for (int i = 0; i < legacy.length; i++) {
            if (i % 2 == 0) { legacy[i] = measure(old); reused[i] = measure(bound); }
            else { reused[i] = measure(bound); legacy[i] = measure(old); }
        }
        Arrays.sort(legacy); Arrays.sort(reused);
        System.out.printf("java=%s ruleViews=%d iterations=10000 samples=7 median_us compile+bind=%.3f bind=%.3f%n",
                System.getProperty("java.version"), compiled.size(), legacy[3], reused[3]);
    }

    private static double measure(Runnable task) {
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) task.run();
        return (System.nanoTime() - start) / 10_000.0 / 1_000;
    }
}

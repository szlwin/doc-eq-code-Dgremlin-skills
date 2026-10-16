import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parse declarations with the JDK AST; no dependency resolution or application execution. */
class JavaSymbolIndex {
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    static void emit(ClassTree type, String owner, String binaryOwner, String pkg, String file, List<String> output) {
        String simple = type.getSimpleName().toString();
        if (simple.isEmpty()) return;
        String name = owner.isEmpty() ? simple : owner + "." + simple;
        String binary = binaryOwner.isEmpty() ? name : binaryOwner + "$" + simple;
        List<String> methods = new ArrayList<>();
        List<String> testMethods = new ArrayList<>();
        for (Tree member : type.getMembers()) {
            if (member instanceof MethodTree method) {
                String methodName = method.getName().toString();
                String signature = methodName + "(" + String.join(",", method.getParameters().stream()
                        .map(p -> p.getType().toString().replaceAll("\\s+", "")).toList()) + ")";
                methods.add(quote(signature));
                if (method.getModifiers().getAnnotations().stream().anyMatch(annotation -> {
                    String typeName = annotation.getAnnotationType().toString();
                    String simpleAnnotation = typeName.substring(typeName.lastIndexOf('.') + 1);
                    return Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate").contains(simpleAnnotation);
                })) testMethods.add(quote(methodName));
            }
        }
        output.add("{\"file\":" + quote(file) + ",\"class\":" + quote(name)
                + ",\"localClass\":" + quote(pkg.isEmpty() ? name : name.substring(pkg.length() + 1))
                + ",\"binaryClass\":" + quote(binary) + ",\"methods\":[" + String.join(",", methods) + "],\"testMethods\":[" + String.join(",", testMethods) + "]}");
        for (Tree member : type.getMembers())
            if (member instanceof ClassTree nested) emit(nested, name, binary, pkg, file, output);
    }
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK is required for Java symbol validation");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> output = new ArrayList<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var paths = Files.readAllLines(Path.of(args[0])).stream().map(Path::of).toList();
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(paths));
            for (CompilationUnitTree unit : task.parse()) {
                String pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                for (Tree type : unit.getTypeDecls())
                    if (type instanceof ClassTree cls) emit(cls, pkg, "", pkg, Path.of(unit.getSourceFile().toUri()).toRealPath().toString(), output);
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic); System.exit(1);
            }
        }
        System.out.println("[" + String.join(",", output) + "]");
    }
}

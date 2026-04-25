package org.mammoth.compiler;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.mammoth.compiler.ast.ProgramNode;
import org.mammoth.compiler.codegen.BytecodeGenerator;
import org.mammoth.compiler.semantic.SemanticAnalyzer;
import org.mammoth.grammar.MammothLexer;
import org.mammoth.grammar.MammothParser;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

public class MammothCompiler {

    public byte[] compile(String sourcePath) throws IOException {
        return compile(sourcePath, null);
    }

    public byte[] compile(String sourcePath, String className) throws IOException {
        String source = Files.readString(Path.of(sourcePath));

        // If className is not specified, derive from filename
        if (className == null) {
            String fileName = Path.of(sourcePath).getFileName().toString();
            // Strip .mp or .php extension
            if (fileName.endsWith(".mp")) {
                className = fileName.substring(0, fileName.length() - 3);
            } else if (fileName.endsWith(".php")) {
                className = fileName.substring(0, fileName.length() - 4);
            } else {
                className = fileName;
            }
        }

        return compileString(source, className, sourcePath);
    }

    public byte[] compileString(String source, String className, String sourceName) {
        // Step 1: Lex and Parse
        MammothLexer lexer = new MammothLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new MammothErrorListener(sourceName));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MammothParser parser = new MammothParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new MammothErrorListener(sourceName));

        MammothParser.ProgramContext parseTree = parser.program();

        // Step 2: Build AST
        AstBuilder astBuilder = new AstBuilder();
        ProgramNode ast = astBuilder.visitProgram(parseTree);

        // Step 3: Semantic Analysis
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(ast);

        if (!analyzer.getErrors().isEmpty()) {
            StringBuilder sb = new StringBuilder("Semantic errors:\n");
            for (String err : analyzer.getErrors()) {
                sb.append("  ").append(err).append("\n");
            }
            throw new RuntimeException(sb.toString());
        }

        // Step 4: Bytecode Generation
        BytecodeGenerator generator = new BytecodeGenerator(analyzer);
        Map<String, byte[]> classFiles = generator.generate(ast);

        // Return the main class (first one, or match className)
        for (Map.Entry<String, byte[]> entry : classFiles.entrySet()) {
            if (entry.getKey().contains(className)) {
                return entry.getValue();
            }
        }

        // Fallback: return first class
        if (!classFiles.isEmpty()) {
            return classFiles.values().iterator().next();
        }

        throw new RuntimeException("No class generated");
    }

    public Map<String, byte[]> compileAll(String sourcePath) throws IOException {
        String source = Files.readString(Path.of(sourcePath));
        return compileAllString(source, sourcePath);
    }

    public Map<String, byte[]> compileAllString(String source, String sourceName) {
        MammothLexer lexer = new MammothLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new MammothErrorListener(sourceName));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MammothParser parser = new MammothParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new MammothErrorListener(sourceName));

        MammothParser.ProgramContext parseTree = parser.program();

        AstBuilder astBuilder = new AstBuilder();
        ProgramNode ast = astBuilder.visitProgram(parseTree);

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(ast);

        if (!analyzer.getErrors().isEmpty()) {
            StringBuilder sb = new StringBuilder("Semantic errors:\n");
            for (String err : analyzer.getErrors()) {
                sb.append("  ").append(err).append("\n");
            }
            throw new RuntimeException(sb.toString());
        }

        BytecodeGenerator generator = new BytecodeGenerator(analyzer);
        return generator.generate(ast);
    }
}

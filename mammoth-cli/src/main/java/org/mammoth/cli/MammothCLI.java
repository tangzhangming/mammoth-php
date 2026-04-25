package org.mammoth.cli;

import org.mammoth.compiler.MammothCompiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class MammothCLI {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        // Determine subcommand from program name or first arg
        String progName = System.getProperty("mammoth.prog", "mammoth");
        String subcommand = args[0];

        if (subcommand.equals("mammothc") || progName.contains("mammothc")) {
            if (subcommand.equals("mammothc")) {
                runCompiler(Arrays.copyOfRange(args, 1, args.length));
            } else {
                runCompiler(args);
            }
        } else {
            // mammoth - run mode
            if (subcommand.equals("mammoth")) {
                runMode(Arrays.copyOfRange(args, 1, args.length));
            } else {
                runMode(args);
            }
        }
    }

    private static void runCompiler(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: mammothc <source.php> [-include-runtime] [-d <output.jar>]");
            System.exit(1);
        }

        String sourceFile = null;
        String outputJar = null;
        boolean includeRuntime = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-d") && i + 1 < args.length) {
                outputJar = args[++i];
            } else if (arg.equals("-include-runtime")) {
                includeRuntime = true;
            } else if (!arg.startsWith("-")) {
                sourceFile = arg;
            }
        }

        if (sourceFile == null) {
            System.err.println("Error: No source file specified");
            System.exit(1);
        }

        MammothCompiler compiler = new MammothCompiler();
        Map<String, byte[]> allClasses = compiler.compileAll(sourceFile);

        if (outputJar != null) {
            String className = deriveClassName(sourceFile);
            byte[] mainClass = allClasses.get(className + ".class");
            if (mainClass == null && !allClasses.isEmpty()) {
                mainClass = allClasses.values().iterator().next();
                String key = allClasses.keySet().iterator().next();
                className = key.substring(0, key.length() - 6);
            }
            createJar(outputJar, className, mainClass, includeRuntime, allClasses);
            System.out.println("Created: " + outputJar);
        } else {
            for (Map.Entry<String, byte[]> entry : allClasses.entrySet()) {
                Files.write(Path.of(entry.getKey()), entry.getValue());
                System.out.println("Compiled: " + sourceFile + " -> " + entry.getKey());
            }
        }
    }

    private static void runMode(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: mammoth <file.php|file.class|file.jar>");
            System.exit(1);
        }

        String targetFile = args[0];
        String stdlibPath = findStdlib();
        String compilerPath = findCompiler();

        if (targetFile.endsWith(".php") || targetFile.endsWith(".mp")) {
            // Compile and run
            MammothCompiler compiler = new MammothCompiler();
            Map<String, byte[]> allClasses = compiler.compileAll(targetFile);
            String className = deriveClassName(targetFile);
            Path tempDir = Files.createTempDirectory("mammoth-run-");

            // Write all generated classes to temp dir
            for (Map.Entry<String, byte[]> entry : allClasses.entrySet()) {
                Files.write(tempDir.resolve(entry.getKey()), entry.getValue());
            }

            // Run with java
            List<String> cmd = new ArrayList<>();
            cmd.add(findJava());
            cmd.add("-cp");
            cmd.add(buildClasspath(tempDir.toString(), stdlibPath, compilerPath));
            cmd.add(className);

            execute(cmd);

            // Cleanup
            for (Map.Entry<String, byte[]> entry : allClasses.entrySet()) {
                Files.deleteIfExists(tempDir.resolve(entry.getKey()));
            }
            Files.deleteIfExists(tempDir);

        } else if (targetFile.endsWith(".class")) {
            // Run class directly
            String className = new File(targetFile).getName();
            className = className.substring(0, className.length() - 6);
            String classDir = new File(targetFile).getAbsoluteFile().getParent();

            List<String> cmd = new ArrayList<>();
            cmd.add(findJava());
            cmd.add("-cp");
            cmd.add(buildClasspath(classDir, stdlibPath, compilerPath));
            cmd.add(className);

            execute(cmd);

        } else if (targetFile.endsWith(".jar")) {
            // Run jar
            List<String> cmd = new ArrayList<>();
            cmd.add(findJava());
            cmd.add("-cp");
            cmd.add(buildClasspath(targetFile, stdlibPath, compilerPath));
            // Need to get main class from manifest
            cmd.add("-jar");
            cmd.add(targetFile);

            execute(cmd);

        } else {
            System.err.println("Error: Unsupported file type: " + targetFile);
            System.exit(1);
        }
    }

    private static String deriveClassName(String sourcePath) {
        String fileName = Path.of(sourcePath).getFileName().toString();
        if (fileName.endsWith(".php")) return fileName.substring(0, fileName.length() - 4);
        if (fileName.endsWith(".mp")) return fileName.substring(0, fileName.length() - 3);
        return fileName;
    }

    private static void createJar(String outputPath, String className, byte[] classBytes,
                                   boolean includeRuntime, Map<String, byte[]> allClasses) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", className);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputPath), manifest)) {
            for (Map.Entry<String, byte[]> entry : allClasses.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }
    }

    private static String buildClasspath(String... paths) {
        return String.join(File.pathSeparator, paths);
    }

    private static String findJava() {
        String javaHome = System.getProperty("java.home");
        String java = javaHome + File.separator + "bin" + File.separator + "java";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            java += ".exe";
        }
        return java;
    }

    private static String findStdlib() {
        // Look for mammoth-stdlib JAR in mammoth-bin/lib
        String mammothHome = System.getenv("MAMMOTH_HOME");
        if (mammothHome != null) {
            Path libDir = Path.of(mammothHome, "lib");
            if (Files.exists(libDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "mammoth-stdlib*.jar")) {
                    for (Path p : ds) {
                        return p.toAbsolutePath().toString();
                    }
                } catch (IOException ignored) {}
            }
        }

        // Fallback: look relative to current jar
        Path currentDir = Path.of("").toAbsolutePath();
        Path libDir = currentDir.resolve("mammoth-bin").resolve("lib");
        if (Files.exists(libDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "mammoth-stdlib*.jar")) {
                for (Path p : ds) {
                    return p.toAbsolutePath().toString();
                }
            } catch (IOException ignored) {}
        }

        return null;
    }

    private static String findCompiler() {
        String mammothHome = System.getenv("MAMMOTH_HOME");
        if (mammothHome != null) {
            Path libDir = Path.of(mammothHome, "lib");
            if (Files.exists(libDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "mammoth-compiler*.jar")) {
                    for (Path p : ds) {
                        return p.toAbsolutePath().toString();
                    }
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static void execute(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        System.exit(exitCode);
    }

    private static void printUsage() {
        System.out.println("mammoth-php " + getVersion());
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  mammothc <source.php>                  Compile source to .class");
        System.out.println("  mammothc <source.php> -d <out.jar>     Compile and package as JAR");
        System.out.println("  mammothc <source.php> -include-runtime -d <out.jar>");
        System.out.println("  mammoth <file.php>                     Compile and run source");
        System.out.println("  mammoth <file.class>                   Run compiled class");
        System.out.println("  mammoth <file.jar>                     Run JAR");
    }

    private static String getVersion() {
        return "0.1.0";
    }
}

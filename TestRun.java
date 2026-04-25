import org.mammoth.compiler.MammothCompiler;
import java.nio.file.*;

public class TestRun {
    public static void main(String[] args) throws Exception {
        MammothCompiler c = new MammothCompiler();
        String src = Files.readString(Path.of(args[0]));
        byte[] bytes = c.compileString(src, "Test", args[0]);
        Files.write(Path.of("Test.class"), bytes);
        new ProcessBuilder("java","-cp",".;mammoth-stdlib/target/classes","Test").inheritIO().start().waitFor();
    }
}

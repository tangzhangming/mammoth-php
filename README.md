# mammoth-php

猛犸PHP —— 一个语法类似 PHP8、编译到 JVM 字节码的静态类型语言。

**技术栈**: ANTLR4 (语法解析) + ASM (字节码生成) + JDK 21

---

## 快速开始

```bash
# 编译项目
mvn package -DskipTests

# 输出在 mammoth-bin/lib/ 下：
#   mammoth-compiler-0.1.0-SNAPSHOT.jar
#   mammoth-cli-0.1.0-SNAPSHOT.jar
#   mammoth-stdlib-0.1.0-SNAPSHOT.jar
```

### 编译 mammoth 源码

```bash
CP="mammoth-bin/lib/*"

# 编译为 .class
java -cp "$CP" org.mammoth.cli.MammothCLI mammothc hello.php

# 编译并打包为 JAR
java -cp "$CP" org.mammoth.cli.MammothCLI mammothc hello.php -d hello.jar
```

### 运行 mammoth 程序

```bash
# 编译 + 运行
java -cp "$CP" org.mammoth.cli.MammothCLI mammoth hello.php

# 直接运行 class
java -cp "$CP" org.mammoth.cli.MammothCLI mammoth Hello.class
```

### 开发调试（直接用编译器 API）

```bash
CP="mammoth-compiler/target/classes:mammoth-stdlib/target/classes:$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar:$HOME/.m2/repository/org/ow2/asm/asm/9.7/asm-9.7.jar"

# 简单测试：写一个 Java 类调用编译器
cat > TestRun.java << 'EOF'
import org.mammoth.compiler.MammothCompiler;
import java.nio.file.*;

public class TestRun {
    public static void main(String[] args) throws Exception {
        MammothCompiler c = new MammothCompiler();
        String src = Files.readString(Path.of(args[0]));
        byte[] bytes = c.compileString(src, "Test", args[0]);
        Files.write(Path.of("Test.class"), bytes);
        new ProcessBuilder("java","-cp",".:mammoth-stdlib/target/classes","Test").inheritIO().start().waitFor();
    }
}
EOF
javac -cp "$CP" TestRun.java
java -cp "$CP:." TestRun playground/test_simple.php
```

---

## 项目结构

```
mammoth-php/
├── pom.xml                      # 父 POM (JDK21, ANTLR 4.13.1, ASM 9.7)
│
├── mammoth-compiler/            # 编译器核心
│   ├── src/main/antlr4/org/mammoth/grammar/
│   │   └── Mammoth.g4           # ANTLR4 语法文件
│   └── src/main/java/org/mammoth/compiler/
│       ├── MammothCompiler.java         # 编译器入口
│       ├── AstBuilder.java              # ParseTree → AST
│       ├── ast/                         # AST 节点 (~25 个类)
│       ├── semantic/                    # 语义分析 + 符号表
│       ├── codegen/                     # ASM 字节码生成
│       └── types/                       # 类型系统 (MammothType)
│
├── mammoth-cli/                 # 命令行入口
│   └── src/main/java/org/mammoth/cli/
│       └── MammothCLI.java              # mammothc / mammoth 命令
│
├── mammoth-stdlib/              # 运行时标准库
│   └── src/main/java/org/mammoth/stdlib/
│       ├── MammothRuntime.java          # 运行时工具
│       └── Ref.java                     # 闭包引用捕获容器
│
├── mammoth-dist/                # 分发打包 (→ mammoth-bin/lib)
│
├── mammoth-bin/
│   ├── bin/                     # 启动脚本
│   └── lib/                     # 构建产物 JAR
│
├── examples/                    # 示例代码 (.php 后缀)
├── playground/                  # 调试草稿
└── docs/
    └── language-features-comparison.md  # 语言特性全景对比表
```

---

## 关键语法速查

```php
<?php
package com.example;          // 或 namespace
import com.example.Foo;        // 或 use

// 类：可见性默认 public（与 Kotlin 一致）
class Application {
    // 入口：静态 main 方法
    public static function main() :void {
        // 变量必须以 $ 开头
        int64 $count = 0;
        string $name = "Mammoth";

        // 类型推断
        $x = 42;                // → int

        // 控制流
        if ($count > 5) { println("big"); }
        while ($count < 3) { $count = $count + 1; }
        for (int64 $i = 0; $i < 5; $i = $i + 1) { println($i); }

        // 异常处理
        try {
            throw new RuntimeException("oops");
        } catch (RuntimeException $e) {
            println("caught");
        } finally {
            println("done");
        }

        // 闭包：值捕获 + 引用捕获
        $fn = function(int64 $x) use ($count) : int64 {
            return $x * $count;
        };
    }
}
```

| 类型 | JVM 描述符 | 别名 |
|------|-----------|------|
| `string` | `Ljava/lang/String;` | - |
| `boolean` | `Z` | - |
| `int8` | `B` | `byte` |
| `int16` | `S` | - |
| `int32` | `I` | - |
| `int64` | `J` | `int` |
| `float32` | `F` | - |
| `float64` | `D` | `float` |
| `void` | `V` | - |
| `nothing` | - | 底部类型，永不返回 |

---

## 语法文件修改后

修改 `Mammoth.g4` 后需要重新编译：

```bash
mvn clean compile -pl mammoth-compiler
```

**注意**: `mvn compile` 会检查增量，如果只改了 `.g4` 没改 `.java`，可能跳过。用 `mvn clean compile` 强制重编译。

---

## 常见问题

**Q: 编译报错 `COMPUTE_FRAMES` / `NegativeArraySizeException`**  
A: 字节码栈帧计算出错。检查生成的字节码是否有死代码或不一致的分支。当前使用 `V1_5 + COMPUTE_MAXS` 规避。

**Q: `java.lang.VerifyError: Expecting to find long on stack`**  
A: 类型不匹配。检查 `generateBinaryOp` 中的算术/比较运算符是否正确插入 `I2L`/`I2D` 转换。

**Q: ANTLR 语法报错 `extraneous input ... expecting ...`**  
A: 检查关键字是否在词法规则中定义，以及解析器规则的备选分支顺序。

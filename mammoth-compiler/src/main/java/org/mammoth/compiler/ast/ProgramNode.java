package org.mammoth.compiler.ast;

import java.util.ArrayList;
import java.util.List;

public class ProgramNode extends MammothNode {
    private String packageName;
    private final List<String> imports = new ArrayList<>();
    private final List<ClassNode> classes = new ArrayList<>();

    public ProgramNode() {
        super(0, 0);
    }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public List<String> getImports() { return imports; }
    public void addImport(String importName) { imports.add(importName); }

    public List<ClassNode> getClasses() { return classes; }
    public void addClass(ClassNode classNode) { classes.add(classNode); }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ClassNode extends MammothNode {
    private final String name;
    private String visibility = "public";
    private final java.util.List<MethodNode> methods = new java.util.ArrayList<>();
    private final java.util.List<FieldNode> fields = new java.util.ArrayList<>();
    private final java.util.List<AnnotationNode> annotations = new java.util.ArrayList<>();
    private final java.util.List<String> enumConstants = new java.util.ArrayList<>();
    private boolean isAnnotation;
    private boolean isEnum;

    public ClassNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public java.util.List<MethodNode> getMethods() { return methods; }
    public void addMethod(MethodNode method) { methods.add(method); }

    public java.util.List<FieldNode> getFields() { return fields; }
    public void addField(FieldNode field) { fields.add(field); }
    public java.util.List<AnnotationNode> getAnnotations() { return annotations; }
    public void addAnnotation(AnnotationNode ann) { annotations.add(ann); }
    public boolean isAnnotation() { return isAnnotation; }
    public void setAnnotation(boolean annotation) { isAnnotation = annotation; }
    public boolean isEnum() { return isEnum; }
    public void setEnum(boolean isEnum) { this.isEnum = isEnum; }
    public java.util.List<String> getEnumConstants() { return enumConstants; }
    public void addEnumConstant(String constant) { enumConstants.add(constant); }
}

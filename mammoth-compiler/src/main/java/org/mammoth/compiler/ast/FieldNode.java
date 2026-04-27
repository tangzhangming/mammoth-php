package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class FieldNode extends MammothNode {
    private final String name;
    private String visibility = "public";
    private boolean isStatic;
    private TypeNode type;
    private ExpressionNode initializer;
    private final java.util.List<AnnotationNode> annotations = new java.util.ArrayList<>();

    public FieldNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean v) { isStatic = v; }
    public TypeNode getType() { return type; }
    public void setType(TypeNode type) { this.type = type; }
    public ExpressionNode getInitializer() { return initializer; }
    public void setInitializer(ExpressionNode initializer) { this.initializer = initializer; }
    public java.util.List<AnnotationNode> getAnnotations() { return annotations; }
    public void addAnnotation(AnnotationNode ann) { annotations.add(ann); }
}

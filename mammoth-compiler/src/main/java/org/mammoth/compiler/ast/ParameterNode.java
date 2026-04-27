package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ParameterNode extends MammothNode {
    private final String name;
    private TypeNode type;
    private ExpressionNode defaultValue;
    private String visibility;        // non-null → property promotion
    private boolean isPromoted;       // true if visibility is present
    private final java.util.List<AnnotationNode> annotations = new java.util.ArrayList<>();

    public ParameterNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public TypeNode getType() { return type; }
    public void setType(TypeNode type) { this.type = type; }
    public ExpressionNode getDefaultValue() { return defaultValue; }
    public void setDefaultValue(ExpressionNode defaultValue) { this.defaultValue = defaultValue; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String v) { this.visibility = v; this.isPromoted = (v != null); }
    public boolean isPromoted() { return isPromoted; }
    public java.util.List<AnnotationNode> getAnnotations() { return annotations; }
    public void addAnnotation(AnnotationNode ann) { annotations.add(ann); }
}

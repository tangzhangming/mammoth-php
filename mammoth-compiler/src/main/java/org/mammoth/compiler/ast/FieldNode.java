package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class FieldNode extends MammothNode {
    private final String name;
    private String visibility = "public";
    private TypeNode type;
    private ExpressionNode initializer;

    public FieldNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public TypeNode getType() { return type; }
    public void setType(TypeNode type) { this.type = type; }
    public ExpressionNode getInitializer() { return initializer; }
    public void setInitializer(ExpressionNode initializer) { this.initializer = initializer; }
}

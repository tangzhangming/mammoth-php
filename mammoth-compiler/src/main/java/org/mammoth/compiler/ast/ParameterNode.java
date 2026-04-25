package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ParameterNode extends MammothNode {
    private final String name;
    private TypeNode type;
    private ExpressionNode defaultValue;

    public ParameterNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public TypeNode getType() { return type; }
    public void setType(TypeNode type) { this.type = type; }
    public ExpressionNode getDefaultValue() { return defaultValue; }
    public void setDefaultValue(ExpressionNode defaultValue) { this.defaultValue = defaultValue; }
}

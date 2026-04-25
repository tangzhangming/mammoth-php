package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class LiteralNode extends MammothNode implements ExpressionNode {
    private final Object value;
    private final String typeHint; // "string", "int", "float", "boolean", "null"

    public LiteralNode(Token token, Object value, String typeHint) {
        super(token);
        this.value = value;
        this.typeHint = typeHint;
    }

    public Object getValue() { return value; }
    public String getTypeHint() { return typeHint; }
}

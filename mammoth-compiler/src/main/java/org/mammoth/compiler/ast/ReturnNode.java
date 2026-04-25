package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ReturnNode extends MammothNode implements StatementNode {
    private final ExpressionNode value;

    public ReturnNode(Token token, ExpressionNode value) {
        super(token);
        this.value = value;
    }

    public ExpressionNode getValue() { return value; }
    public boolean hasValue() { return value != null; }
}

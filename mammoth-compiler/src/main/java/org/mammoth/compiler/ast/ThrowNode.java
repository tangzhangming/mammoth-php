package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ThrowNode extends MammothNode implements StatementNode {
    private final ExpressionNode expression;

    public ThrowNode(Token token, ExpressionNode expression) {
        super(token);
        this.expression = expression;
    }

    public ExpressionNode getExpression() { return expression; }
}

package org.mammoth.compiler.ast;

public class ExpressionStatementNode extends MammothNode implements StatementNode {
    private final ExpressionNode expression;

    public ExpressionStatementNode(ExpressionNode expression) {
        super(0, 0);
        this.expression = expression;
    }

    public ExpressionNode getExpression() { return expression; }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class WhileNode extends MammothNode implements StatementNode {
    private final ExpressionNode condition;
    private StatementNode body;

    public WhileNode(Token token, ExpressionNode condition, StatementNode body) {
        super(token);
        this.condition = condition;
        this.body = body;
    }

    public ExpressionNode getCondition() { return condition; }
    public StatementNode getBody() { return body; }
    public void setBody(StatementNode body) { this.body = body; }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ForNode extends MammothNode implements StatementNode {
    private StatementNode init;
    private final ExpressionNode condition;
    private final ExpressionNode update;
    private StatementNode body;

    public ForNode(Token token, StatementNode init, ExpressionNode condition, ExpressionNode update, StatementNode body) {
        super(token);
        this.init = init;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    public StatementNode getInit() { return init; }
    public void setInit(StatementNode init) { this.init = init; }
    public ExpressionNode getCondition() { return condition; }
    public ExpressionNode getUpdate() { return update; }
    public StatementNode getBody() { return body; }
    public void setBody(StatementNode body) { this.body = body; }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class IfNode extends MammothNode implements StatementNode {
    private final ExpressionNode condition;
    private StatementNode thenBranch;
    private StatementNode elseBranch;

    public IfNode(Token token, ExpressionNode condition, StatementNode thenBranch, StatementNode elseBranch) {
        super(token);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public ExpressionNode getCondition() { return condition; }
    public StatementNode getThenBranch() { return thenBranch; }
    public void setThenBranch(StatementNode thenBranch) { this.thenBranch = thenBranch; }
    public StatementNode getElseBranch() { return elseBranch; }
    public void setElseBranch(StatementNode elseBranch) { this.elseBranch = elseBranch; }
    public boolean hasElse() { return elseBranch != null; }
}

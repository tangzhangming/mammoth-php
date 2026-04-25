package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class AssignmentNode extends MammothNode implements ExpressionNode {
    private final VariableNode target;
    private final ExpressionNode value;

    public AssignmentNode(Token token, VariableNode target, ExpressionNode value) {
        super(token);
        this.target = target;
        this.value = value;
    }

    public VariableNode getTarget() { return target; }
    public ExpressionNode getValue() { return value; }
}

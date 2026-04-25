package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class BinaryOpNode extends MammothNode implements ExpressionNode {
    private final ExpressionNode left;
    private final String op;
    private final ExpressionNode right;

    public BinaryOpNode(Token token, ExpressionNode left, String op, ExpressionNode right) {
        super(token);
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public ExpressionNode getLeft() { return left; }
    public String getOp() { return op; }
    public ExpressionNode getRight() { return right; }
}

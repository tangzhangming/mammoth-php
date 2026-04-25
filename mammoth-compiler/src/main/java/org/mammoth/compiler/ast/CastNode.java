package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class CastNode extends MammothNode implements ExpressionNode {
    private final TypeNode targetType;
    private final ExpressionNode expression;

    public CastNode(Token token, TypeNode targetType, ExpressionNode expression) {
        super(token);
        this.targetType = targetType;
        this.expression = expression;
    }

    public TypeNode getTargetType() { return targetType; }
    public ExpressionNode getExpression() { return expression; }
}

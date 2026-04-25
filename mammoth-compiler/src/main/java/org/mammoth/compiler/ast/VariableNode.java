package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.types.MammothType;

public class VariableNode extends MammothNode implements ExpressionNode {
    private final String name;
    private TypeNode inferredType;
    private MammothType resolvedType;
    private int localIndex = -1;

    public VariableNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public TypeNode getInferredType() { return inferredType; }
    public void setInferredType(TypeNode inferredType) { this.inferredType = inferredType; }
    public MammothType getResolvedType() { return resolvedType; }
    public void setResolvedType(MammothType resolvedType) { this.resolvedType = resolvedType; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int localIndex) { this.localIndex = localIndex; }
}

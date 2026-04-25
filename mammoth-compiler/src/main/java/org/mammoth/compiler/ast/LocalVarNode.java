package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.types.MammothType;

public class LocalVarNode extends MammothNode implements StatementNode {
    private final String name;
    private TypeNode type;
    private ExpressionNode initializer;
    private MammothType resolvedType;
    private int localIndex = -1;

    public LocalVarNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public TypeNode getType() { return type; }
    public void setType(TypeNode type) { this.type = type; }
    public ExpressionNode getInitializer() { return initializer; }
    public void setInitializer(ExpressionNode initializer) { this.initializer = initializer; }
    public MammothType getResolvedType() { return resolvedType; }
    public void setResolvedType(MammothType resolvedType) { this.resolvedType = resolvedType; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int localIndex) { this.localIndex = localIndex; }
}

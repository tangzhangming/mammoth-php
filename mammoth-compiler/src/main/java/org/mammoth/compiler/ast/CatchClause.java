package org.mammoth.compiler.ast;

import org.mammoth.compiler.types.MammothType;

public class CatchClause {
    private final TypeNode exceptionType;
    private final String variableName;
    private BlockNode body;
    private MammothType resolvedType;
    private int localIndex = -1;

    public CatchClause(TypeNode exceptionType, String variableName) {
        this.exceptionType = exceptionType;
        this.variableName = variableName;
    }

    public TypeNode getExceptionType() { return exceptionType; }
    public String getVariableName() { return variableName; }
    public BlockNode getBody() { return body; }
    public void setBody(BlockNode body) { this.body = body; }
    public MammothType getResolvedType() { return resolvedType; }
    public void setResolvedType(MammothType resolvedType) { this.resolvedType = resolvedType; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int localIndex) { this.localIndex = localIndex; }

    public String getJvmVariableName() {
        return variableName.startsWith("$") ? variableName.substring(1) : variableName;
    }
}

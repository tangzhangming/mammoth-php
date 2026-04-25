package org.mammoth.compiler.ast;

public class TypeNode extends MammothNode {
    private final String baseTypeName;
    private final boolean nullable;
    private Object resolvedType; // Set by semantic analysis

    public TypeNode(String baseTypeName, boolean nullable) {
        super(0, 0);
        this.baseTypeName = baseTypeName;
        this.nullable = nullable;
    }

    public String getBaseTypeName() { return baseTypeName; }
    public boolean isNullable() { return nullable; }

    public Object getResolvedType() { return resolvedType; }
    public void setResolvedType(Object resolvedType) { this.resolvedType = resolvedType; }

    public boolean isInferred() { return baseTypeName == null; }

    @Override
    public String toString() {
        return nullable ? "?" + baseTypeName : baseTypeName;
    }
}

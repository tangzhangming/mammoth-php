package org.mammoth.compiler.semantic;

import org.mammoth.compiler.ast.TypeNode;
import org.mammoth.compiler.types.MammothType;

public class Symbol {
    private final String name;
    private final SymbolKind kind;
    private TypeNode typeNode;
    private MammothType resolvedType;
    private int localIndex = -1;
    private boolean isInitialized;

    public Symbol(String name, SymbolKind kind) {
        this.name = name;
        this.kind = kind;
    }

    public String getName() { return name; }
    public SymbolKind getKind() { return kind; }
    public TypeNode getTypeNode() { return typeNode; }
    public void setTypeNode(TypeNode typeNode) { this.typeNode = typeNode; }
    public MammothType getResolvedType() { return resolvedType; }
    public void setResolvedType(MammothType resolvedType) { this.resolvedType = resolvedType; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int localIndex) { this.localIndex = localIndex; }
    public boolean isInitialized() { return isInitialized; }
    public void setInitialized(boolean initialized) { isInitialized = initialized; }

    public enum SymbolKind {
        VARIABLE,
        FIELD,
        METHOD,
        PARAMETER,
        CLASS
    }
}

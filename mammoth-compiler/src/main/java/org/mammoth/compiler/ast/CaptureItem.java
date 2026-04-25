package org.mammoth.compiler.ast;

public class CaptureItem {
    private final String variableName;
    private final boolean byReference;
    private int localIndex = -1;
    private Object resolvedType; // MammothType, set during semantic analysis

    public CaptureItem(String variableName, boolean byReference) {
        this.variableName = variableName;
        this.byReference = byReference;
    }

    public String getVariableName() { return variableName; }
    public boolean isByReference() { return byReference; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int localIndex) { this.localIndex = localIndex; }
    public Object getResolvedType() { return resolvedType; }
    public void setResolvedType(Object resolvedType) { this.resolvedType = resolvedType; }

    public String getJvmName() {
        return variableName.startsWith("$") ? variableName.substring(1) : variableName;
    }
}

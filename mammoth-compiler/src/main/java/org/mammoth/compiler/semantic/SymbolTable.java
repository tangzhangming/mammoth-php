package org.mammoth.compiler.semantic;

import java.util.*;

public class SymbolTable {
    private final List<Map<String, Symbol>> scopes = new ArrayList<>();
    private final Map<String, Symbol> globalSymbols = new LinkedHashMap<>();
    private int localVarIndex;

    public SymbolTable() {
        pushScope(); // Global scope
    }

    public void pushScope() {
        scopes.add(new LinkedHashMap<>());
    }

    public void popScope() {
        if (scopes.size() > 1) {
            scopes.remove(scopes.size() - 1);
        }
    }

    public void enterGlobalScope() {
        // reset for a new class
        scopes.clear();
        globalSymbols.clear();
        localVarIndex = 0;
        pushScope();
    }

    public void define(Symbol symbol) {
        Map<String, Symbol> currentScope = scopes.get(scopes.size() - 1);
        currentScope.put(symbol.getName(), symbol);
        if (scopes.size() == 1) {
            globalSymbols.put(symbol.getName(), symbol);
        }
    }

    public Symbol resolve(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Symbol sym = scopes.get(i).get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    public Symbol resolveCurrentScope(String name) {
        return scopes.get(scopes.size() - 1).get(name);
    }

    public int allocateLocalVar() {
        return localVarIndex++;
    }

    public int allocateLocalVar(boolean isWide) {
        int idx = localVarIndex;
        localVarIndex += isWide ? 2 : 1;
        return idx;
    }

    public int getLocalVarCount() {
        return localVarIndex;
    }

    public void resetLocalVarCount() {
        localVarIndex = 0;
    }

    public Map<String, Symbol> getGlobalSymbols() {
        return globalSymbols;
    }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public abstract class MammothNode {
    private final int line;
    private final int column;

    protected MammothNode(Token token) {
        this.line = token != null ? token.getLine() : 0;
        this.column = token != null ? token.getCharPositionInLine() : 0;
    }

    protected MammothNode(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }
}

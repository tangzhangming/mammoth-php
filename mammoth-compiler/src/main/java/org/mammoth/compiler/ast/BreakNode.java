package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class BreakNode extends MammothNode implements StatementNode {

    public BreakNode(Token token) {
        super(token);
    }
}

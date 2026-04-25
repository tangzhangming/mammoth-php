package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ContinueNode extends MammothNode implements StatementNode {

    public ContinueNode(Token token) {
        super(token);
    }
}

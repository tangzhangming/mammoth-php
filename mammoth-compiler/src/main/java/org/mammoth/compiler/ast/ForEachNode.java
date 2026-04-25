package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class ForEachNode extends MammothNode implements StatementNode {
    private final ExpressionNode iterable;
    private final String keyVar;
    private final String valueVar;
    private StatementNode body;

    public ForEachNode(Token token, ExpressionNode iterable, String keyVar, String valueVar, StatementNode body) {
        super(token);
        this.iterable = iterable;
        this.keyVar = keyVar;
        this.valueVar = valueVar;
        this.body = body;
    }

    public ExpressionNode getIterable() { return iterable; }
    public String getKeyVar() { return keyVar; }
    public boolean hasKeyVar() { return keyVar != null; }
    public String getValueVar() { return valueVar; }
    public StatementNode getBody() { return body; }
    public void setBody(StatementNode body) { this.body = body; }
}

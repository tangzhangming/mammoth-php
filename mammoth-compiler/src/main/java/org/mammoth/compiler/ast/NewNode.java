package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import java.util.ArrayList;
import java.util.List;

public class NewNode extends MammothNode implements ExpressionNode {
    private final String className;
    private final List<ExpressionNode> arguments = new ArrayList<>();

    public NewNode(Token token, String className) {
        super(token);
        this.className = className;
    }

    public String getClassName() { return className; }
    public List<ExpressionNode> getArguments() { return arguments; }
    public void addArgument(ExpressionNode arg) { arguments.add(arg); }
}

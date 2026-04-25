package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import java.util.ArrayList;
import java.util.List;

public class ClosureNode extends MammothNode implements ExpressionNode {
    private final List<ParameterNode> parameters = new ArrayList<>();
    private final List<CaptureItem> captures = new ArrayList<>();
    private TypeNode returnType;
    private BlockNode body;
    private boolean isArrowFunction;

    public ClosureNode(Token token) {
        super(token);
    }

    public List<ParameterNode> getParameters() { return parameters; }
    public void addParameter(ParameterNode param) { parameters.add(param); }

    public List<CaptureItem> getCaptures() { return captures; }
    public void addCapture(CaptureItem capture) { captures.add(capture); }

    public TypeNode getReturnType() { return returnType; }
    public void setReturnType(TypeNode returnType) { this.returnType = returnType; }

    public BlockNode getBody() { return body; }
    public void setBody(BlockNode body) { this.body = body; }

    public boolean hasReferences() {
        return captures.stream().anyMatch(CaptureItem::isByReference);
    }

    public boolean isArrowFunction() { return isArrowFunction; }
    public void setArrowFunction(boolean isArrowFunction) { this.isArrowFunction = isArrowFunction; }
}

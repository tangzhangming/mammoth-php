package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.types.MammothType;
import java.util.ArrayList;
import java.util.List;

public class MethodCallNode extends MammothNode implements ExpressionNode {
    private final String methodName;
    private final List<ExpressionNode> arguments = new ArrayList<>();
    private boolean isBuiltinPrint;
    private boolean isVariableCall;
    private MammothType targetType;
    private int targetLocalIndex = -1;

    public MethodCallNode(Token token, String methodName) {
        super(token);
        this.methodName = methodName;
        this.isVariableCall = methodName.startsWith("$");
    }

    public String getMethodName() { return methodName; }
    public List<ExpressionNode> getArguments() { return arguments; }
    public void addArgument(ExpressionNode arg) { arguments.add(arg); }
    public boolean isBuiltinPrint() { return isBuiltinPrint; }
    public void setBuiltinPrint(boolean isBuiltinPrint) { this.isBuiltinPrint = isBuiltinPrint; }
    public boolean isVariableCall() { return isVariableCall; }
    public MammothType getTargetType() { return targetType; }
    public void setTargetType(MammothType targetType) { this.targetType = targetType; }
    public int getTargetLocalIndex() { return targetLocalIndex; }
    public void setTargetLocalIndex(int targetLocalIndex) { this.targetLocalIndex = targetLocalIndex; }
}

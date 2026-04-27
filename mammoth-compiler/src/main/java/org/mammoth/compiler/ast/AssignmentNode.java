package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;

public class AssignmentNode extends MammothNode implements ExpressionNode {
    private final VariableNode target;
    private final ExpressionNode value;
    private boolean isStaticAssign;    // self::$var = expr or ClassName::$var = expr
    private boolean isMemberAssign;    // $this->prop = expr
    private String staticClassName;    // "self" or class name, for static assign

    public AssignmentNode(Token token, VariableNode target, ExpressionNode value) {
        super(token);
        this.target = target;
        this.value = value;
    }

    public VariableNode getTarget() { return target; }
    public ExpressionNode getValue() { return value; }
    public boolean isStaticAssign() { return isStaticAssign; }
    public void setStaticAssign(String className) { this.isStaticAssign = true; this.staticClassName = className; }
    public boolean isMemberAssign() { return isMemberAssign; }
    public void setMemberAssign(boolean v) { isMemberAssign = v; }
    public String getStaticClassName() { return staticClassName; }
}

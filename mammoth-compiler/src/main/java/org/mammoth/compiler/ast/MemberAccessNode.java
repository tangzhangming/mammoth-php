package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.types.MammothType;
import java.util.ArrayList;
import java.util.List;

/**
 * Instance member access: expr->member or expr->method(args).
 * Generated from: expression THIN_ARROW IDENTIFIER (LPAREN arguments? RPAREN)?
 */
public class MemberAccessNode extends MammothNode implements ExpressionNode {
    private final ExpressionNode target;
    private final String memberName;
    private final List<ExpressionNode> args;

    // Resolved info (set by semantic analysis)
    private boolean isMethodCall;
    private MammothType fieldType;       // for field access
    private String fieldOwner;           // internal name of owning class
    private String fieldDescriptor;      // JVM descriptor of field

    public MemberAccessNode(Token token, ExpressionNode target, String memberName, List<ExpressionNode> args) {
        super(token);
        this.target = target;
        this.memberName = memberName;
        this.args = args != null ? args : new ArrayList<>();
        this.isMethodCall = args != null && !args.isEmpty();
    }

    public ExpressionNode getTarget() { return target; }
    public String getMemberName() { return memberName; }
    public List<ExpressionNode> getArgs() { return args; }
    public boolean isMethodCall() { return isMethodCall; }
    public void setMethodCall(boolean v) { isMethodCall = v; }

    public MammothType getFieldType() { return fieldType; }
    public void setFieldType(MammothType t) { fieldType = t; }
    public String getFieldOwner() { return fieldOwner; }
    public void setFieldOwner(String o) { fieldOwner = o; }
    public String getFieldDescriptor() { return fieldDescriptor; }
    public void setFieldDescriptor(String d) { fieldDescriptor = d; }
}

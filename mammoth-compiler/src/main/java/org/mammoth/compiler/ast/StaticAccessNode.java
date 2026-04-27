package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.types.MammothType;
import java.util.ArrayList;
import java.util.List;

/**
 * Static member access: ClassName::member, self::member, parent::method(args).
 * Generated from: (SELF | PARENT | IDENTIFIER) DOUBLE_COLON (VARIABLE | IDENTIFIER) (LPAREN arguments? RPAREN)?
 */
public class StaticAccessNode extends MammothNode implements ExpressionNode {
    private final String className;   // "self", "parent", or actual class name
    private final String memberName;  // field name (with $) or method name
    private final List<ExpressionNode> args;
    private boolean isMethodCall;

    // Resolved info (set by semantic analysis / codegen)
    private String resolvedOwner;       // internal name of the resolved class
    private MammothType resolvedType;   // for field access
    private int localIndex = -1;        // for field load/store

    public StaticAccessNode(Token token, String className, String memberName, List<ExpressionNode> args) {
        super(token);
        this.className = className;
        this.memberName = memberName;
        this.args = args != null ? args : new ArrayList<>();
        this.isMethodCall = args != null && !args.isEmpty();
    }

    public String getClassName() { return className; }
    public String getMemberName() { return memberName; }
    public List<ExpressionNode> getArgs() { return args; }
    public boolean isMethodCall() { return isMethodCall; }
    public void setMethodCall(boolean v) { isMethodCall = v; }
    public boolean isSelfCall() { return "self".equals(className); }
    public boolean isParentCall() { return "parent".equals(className); }

    public String getResolvedOwner() { return resolvedOwner; }
    public void setResolvedOwner(String o) { resolvedOwner = o; }
    public MammothType getResolvedType() { return resolvedType; }
    public void setResolvedType(MammothType t) { resolvedType = t; }
    public int getLocalIndex() { return localIndex; }
    public void setLocalIndex(int idx) { localIndex = idx; }
}

package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import java.util.ArrayList;
import java.util.List;

public class MethodNode extends MammothNode {
    private final String name;
    private String visibility = "public";
    private boolean isStatic;
    private TypeNode returnType;
    private final List<ParameterNode> parameters = new ArrayList<>();
    private BlockNode body;

    public MethodNode(Token token, String name) {
        super(token);
        this.name = name;
    }

    public String getName() { return name; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean isStatic) { this.isStatic = isStatic; }
    public TypeNode getReturnType() { return returnType; }
    public void setReturnType(TypeNode returnType) { this.returnType = returnType; }
    public List<ParameterNode> getParameters() { return parameters; }
    public void addParameter(ParameterNode param) { parameters.add(param); }
    public BlockNode getBody() { return body; }
    public void setBody(BlockNode body) { this.body = body; }
}

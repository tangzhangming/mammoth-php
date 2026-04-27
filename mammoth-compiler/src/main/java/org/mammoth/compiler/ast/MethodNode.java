package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import java.util.ArrayList;
import java.util.List;

public class MethodNode extends MammothNode {
    private final String name;
    private String visibility = "public";
    private boolean isStatic;
    private boolean isAbstract;
    private boolean isConstructor;
    private TypeNode returnType;
    private final List<ParameterNode> parameters = new ArrayList<>();
    private BlockNode body;
    private final java.util.List<AnnotationNode> annotations = new java.util.ArrayList<>();

    public MethodNode(Token token, String name) {
        super(token);
        this.name = name;
        this.isConstructor = "__construct".equals(name);
    }

    public String getName() { return name; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean isStatic) { this.isStatic = isStatic; }
    public boolean isAbstract() { return isAbstract; }
    public void setAbstract(boolean isAbstract) { this.isAbstract = isAbstract; }
    public boolean isConstructor() { return isConstructor; }
    public TypeNode getReturnType() { return returnType; }
    public void setReturnType(TypeNode returnType) { this.returnType = returnType; }
    public List<ParameterNode> getParameters() { return parameters; }
    public void addParameter(ParameterNode param) { parameters.add(param); }
    public BlockNode getBody() { return body; }
    public void setBody(BlockNode body) { this.body = body; }
    public java.util.List<AnnotationNode> getAnnotations() { return annotations; }
    public void addAnnotation(AnnotationNode ann) { annotations.add(ann); }
}

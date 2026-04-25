package org.mammoth.compiler.ast;

import org.antlr.v4.runtime.Token;
import java.util.ArrayList;
import java.util.List;

public class AnnotationNode extends MammothNode {
    private final String typeName;
    private final List<AnnotationArg> args = new ArrayList<>();
    private String target; // null, "field", "get", "set"

    public AnnotationNode(Token token, String typeName) {
        super(token);
        this.typeName = typeName;
    }

    public String getTypeName() { return typeName; }
    public List<AnnotationArg> getArgs() { return args; }
    public void addArg(AnnotationArg arg) { args.add(arg); }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public static class AnnotationArg {
        private final String name; // null for positional
        private final ExpressionNode value;
        public AnnotationArg(String name, ExpressionNode value) {
            this.name = name; this.value = value;
        }
        public String getName() { return name; }
        public boolean isNamed() { return name != null; }
        public ExpressionNode getValue() { return value; }
    }
}

package org.mammoth.compiler.ast;

import java.util.ArrayList;
import java.util.List;

public class BlockNode extends MammothNode implements StatementNode {
    private final List<StatementNode> statements = new ArrayList<>();

    public BlockNode() {
        super(0, 0);
    }

    public List<StatementNode> getStatements() { return statements; }
    public void addStatement(StatementNode stmt) { statements.add(stmt); }
}

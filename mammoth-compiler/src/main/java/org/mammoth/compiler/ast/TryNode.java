package org.mammoth.compiler.ast;

import java.util.ArrayList;
import java.util.List;

public class TryNode extends MammothNode implements StatementNode {
    private BlockNode tryBlock;
    private final List<CatchClause> catchClauses = new ArrayList<>();
    private BlockNode finallyBlock;

    public TryNode(int line, int col) {
        super(line, col);
    }

    public BlockNode getTryBlock() { return tryBlock; }
    public void setTryBlock(BlockNode tryBlock) { this.tryBlock = tryBlock; }

    public List<CatchClause> getCatchClauses() { return catchClauses; }
    public void addCatchClause(CatchClause clause) { catchClauses.add(clause); }

    public BlockNode getFinallyBlock() { return finallyBlock; }
    public void setFinallyBlock(BlockNode finallyBlock) { this.finallyBlock = finallyBlock; }

    public boolean hasFinally() { return finallyBlock != null; }
}

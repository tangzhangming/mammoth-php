package org.mammoth.compiler.semantic;

import org.mammoth.compiler.ast.*;
import org.mammoth.compiler.types.MammothType;

import java.util.*;

public class SemanticAnalyzer {
    private final SymbolTable symbolTable = new SymbolTable();
    private final List<String> errors = new ArrayList<>();
    private ClassNode currentClass;

    public List<String> getErrors() { return errors; }

    public SymbolTable getSymbolTable() { return symbolTable; }

    public void analyze(ProgramNode program) {
        String packageName = program.getPackageName();
        for (ClassNode cls : program.getClasses()) {
            analyzeClass(cls);
        }
    }

    private void analyzeClass(ClassNode cls) {
        currentClass = cls;
        symbolTable.enterGlobalScope();

        // Register fields in class scope
        for (FieldNode field : cls.getFields()) {
            String name = field.getName();
            Symbol sym = new Symbol(name, Symbol.SymbolKind.FIELD);
            sym.setTypeNode(field.getType());

            if (field.getType() != null) {
                sym.setResolvedType(resolveType(field.getType()));
            }

            // Fields don't need to be initialized in declaration (can be null)
            sym.setInitialized(true);
            symbolTable.define(sym);

            if (field.getInitializer() != null) {
                resolveExpression(field.getInitializer());
            }
        }

        // Analyze methods
        for (MethodNode method : cls.getMethods()) {
            analyzeMethod(method);
        }
        currentClass = null;
    }

    private void analyzeMethod(MethodNode method) {
        symbolTable.pushScope();
        symbolTable.resetLocalVarCount();

        // Register parameters
        // For main method without parameters, we still generate String[] args in bytecode
        boolean isMain = method.getName().equals("main") && method.isStatic();
        if (isMain && method.getParameters().isEmpty()) {
            // main() without args - this is fine, the bytecode generator handles it
        }

        for (ParameterNode param : method.getParameters()) {
            String name = param.getName();
            Symbol sym = new Symbol(name, Symbol.SymbolKind.PARAMETER);
            sym.setTypeNode(param.getType());

            if (param.getType() != null) {
                MammothType mt = resolveType(param.getType());
                sym.setResolvedType(mt);
                boolean isWide = mt == MammothType.INT64 || mt == MammothType.FLOAT64;
                sym.setLocalIndex(symbolTable.allocateLocalVar(isWide));
            } else {
                sym.setLocalIndex(symbolTable.allocateLocalVar(false));
            }

            sym.setInitialized(true); // Parameters are always initialized

            if (param.getDefaultValue() != null) {
                resolveExpression(param.getDefaultValue());
            }
            symbolTable.define(sym);
        }

        // Resolve return type
        if (method.getReturnType() != null) {
            resolveType(method.getReturnType());
        }

        // Analyze body
        if (method.getBody() != null) {
            analyzeBlock(method.getBody());
        }

        symbolTable.popScope();
    }

    private void analyzeBlock(BlockNode block) {
        symbolTable.pushScope();
        for (StatementNode stmt : block.getStatements()) {
            analyzeStatement(stmt);
        }
        symbolTable.popScope();
    }

    private void analyzeStatement(StatementNode stmt) {
        if (stmt instanceof ExpressionStatementNode es) {
            resolveExpression(es.getExpression());
        } else if (stmt instanceof ReturnNode rn) {
            if (rn.hasValue()) {
                resolveExpression(rn.getValue());
            }
        } else if (stmt instanceof LocalVarNode lvn) {
            String name = lvn.getName();
            Symbol sym = new Symbol(name, Symbol.SymbolKind.VARIABLE);
            sym.setTypeNode(lvn.getType());
            MammothType mt = resolveType(lvn.getType());
            sym.setResolvedType(mt);
            boolean isWide = mt == MammothType.INT64 || mt == MammothType.FLOAT64;
            sym.setLocalIndex(symbolTable.allocateLocalVar(isWide));
            sym.setInitialized(true);
            symbolTable.define(sym);
            lvn.setResolvedType(sym.getResolvedType());
            lvn.setLocalIndex(sym.getLocalIndex());
            if (lvn.getInitializer() != null) {
                resolveExpression(lvn.getInitializer());
            }
        } else if (stmt instanceof BlockNode bn) {
            analyzeBlock(bn);
        }
    }

    public void resolveExpression(ExpressionNode expr) {
        if (expr instanceof LiteralNode) {
            // Nothing to resolve
        } else if (expr instanceof VariableNode vn) {
            resolveVariable(vn);
        } else if (expr instanceof AssignmentNode an) {
            resolveVariable(an.getTarget());
            // For recursive closures: register forward declaration before resolving the value
            if (an.getValue() instanceof ClosureNode cn && cn.getCaptures().stream()
                    .anyMatch(c -> c.getVariableName().equals(an.getTarget().getName()))) {
                VariableNode target = an.getTarget();
                if (symbolTable.resolveCurrentScope(target.getName()) == null) {
                    Symbol sym = new Symbol(target.getName(), Symbol.SymbolKind.VARIABLE);
                    sym.setTypeNode(new TypeNode("string", false));
                    sym.setResolvedType(MammothType.STRING);
                    sym.setLocalIndex(symbolTable.allocateLocalVar(false));
                    sym.setInitialized(false); // not yet initialized
                    symbolTable.define(sym);
                }
            }
            resolveExpression(an.getValue());
            if (an.getTarget().getInferredType() == null) {
                inferTypeFromExpression(an.getTarget(), an.getValue());
            }
        } else if (expr instanceof MethodCallNode mcn) {
            for (ExpressionNode arg : mcn.getArguments()) {
                resolveExpression(arg);
            }
            // If this is a variable call ($fn(...)), resolve the target variable
            if (mcn.isVariableCall()) {
                Symbol sym = symbolTable.resolve(mcn.getMethodName());
                if (sym != null) {
                    mcn.setTargetType(sym.getResolvedType());
                    mcn.setTargetLocalIndex(sym.getLocalIndex());
                }
            }
        } else if (expr instanceof BinaryOpNode bon) {
            resolveExpression(bon.getLeft());
            resolveExpression(bon.getRight());
        } else if (expr instanceof CastNode cn) {
            resolveType(cn.getTargetType());
            resolveExpression(cn.getExpression());
        } else if (expr instanceof ClosureNode cn) {
            analyzeClosure(cn);
        }
    }

    private void analyzeClosure(ClosureNode closure) {
        // Analyze closure parameters
        for (ParameterNode param : closure.getParameters()) {
            if (param.getType() != null) {
                resolveType(param.getType());
            }
            if (param.getDefaultValue() != null) {
                resolveExpression(param.getDefaultValue());
            }
        }

        // Resolve return type
        if (closure.getReturnType() != null) {
            resolveType(closure.getReturnType());
        }

        // Resolve captured variables against the outer scope
        for (CaptureItem capture : closure.getCaptures()) {
            String varName = capture.getVariableName();
            Symbol sym = symbolTable.resolve(varName);
            if (sym == null) {
                errors.add("Undefined variable in use clause: " + varName);
            } else {
                capture.setLocalIndex(sym.getLocalIndex());
                capture.setResolvedType(sym.getResolvedType());
            }
        }

        // Analyze closure body in a new scope
        symbolTable.pushScope();
        symbolTable.resetLocalVarCount();

        // Register closure parameters in the closure scope
        for (ParameterNode param : closure.getParameters()) {
            String name = param.getName();
            Symbol sym = new Symbol(name, Symbol.SymbolKind.PARAMETER);
            sym.setTypeNode(param.getType());
            if (param.getType() != null) {
                MammothType mt = resolveType(param.getType());
                sym.setResolvedType(mt);
                boolean isWide = mt == MammothType.INT64 || mt == MammothType.FLOAT64;
                sym.setLocalIndex(symbolTable.allocateLocalVar(isWide));
            } else {
                sym.setLocalIndex(symbolTable.allocateLocalVar(false));
            }
            sym.setInitialized(true);
            symbolTable.define(sym);
        }

        // Register captured variables in the closure scope
        for (CaptureItem capture : closure.getCaptures()) {
            String name = capture.getVariableName();
            Symbol outerSym = symbolTable.resolve(name);  // resolve from outer scope
            if (outerSym != null) {
                Symbol innerSym = new Symbol(name, Symbol.SymbolKind.VARIABLE);
                innerSym.setTypeNode(outerSym.getTypeNode());
                innerSym.setResolvedType(outerSym.getResolvedType());
                innerSym.setLocalIndex(symbolTable.allocateLocalVar(false)); // Ref or direct value
                innerSym.setInitialized(true);
                symbolTable.define(innerSym);
            }
        }

        // Analyze closure body
        if (closure.getBody() != null) {
            analyzeBlock(closure.getBody());
        }

        symbolTable.popScope();
    }

    private void resolveVariable(VariableNode vn) {
        String name = vn.getName();
        Symbol sym = symbolTable.resolve(name);
        if (sym != null) {
            vn.setInferredType(sym.getTypeNode());
            vn.setResolvedType(sym.getResolvedType());
            vn.setLocalIndex(sym.getLocalIndex());
        }
    }

    private void inferTypeFromExpression(VariableNode target, ExpressionNode value) {
        TypeNode inferred = null;
        MammothType mt = null;
        if (value instanceof LiteralNode ln) {
            mt = MammothType.inferFromLiteral(ln.getTypeHint());
            inferred = new TypeNode(mt.getMammothName(), false);
        } else if (value instanceof VariableNode vn) {
            Symbol sym = symbolTable.resolve(vn.getName());
            if (sym != null) {
                inferred = sym.getTypeNode();
                mt = sym.getResolvedType();
            }
        } else if (value instanceof BinaryOpNode bon) {
            resolveExpression(bon.getLeft());
        } else if (value instanceof ClosureNode) {
            inferred = new TypeNode("string", false);
            mt = MammothType.STRING;
        }

        if (inferred != null && mt != null) {
            target.setInferredType(inferred);
            target.setResolvedType(mt);

            if (symbolTable.resolveCurrentScope(target.getName()) == null) {
                boolean isWide = mt == MammothType.INT64 || mt == MammothType.FLOAT64;
                Symbol sym = new Symbol(target.getName(), Symbol.SymbolKind.VARIABLE);
                sym.setTypeNode(inferred);
                sym.setResolvedType(mt);
                sym.setLocalIndex(symbolTable.allocateLocalVar(isWide));
                sym.setInitialized(true);
                symbolTable.define(sym);
                target.setLocalIndex(sym.getLocalIndex());
            } else {
                Symbol sym = symbolTable.resolve(target.getName());
                if (sym != null) {
                    target.setLocalIndex(sym.getLocalIndex());
                }
            }
        }
    }

    public MammothType resolveType(TypeNode typeNode) {
        if (typeNode.getResolvedType() instanceof MammothType mt) {
            return mt;
        }
        MammothType resolved = MammothType.fromTypeName(typeNode.getBaseTypeName());
        typeNode.setResolvedType(resolved);
        return resolved;
    }
}

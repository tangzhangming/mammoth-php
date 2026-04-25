package org.mammoth.compiler;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.ast.*;
import org.mammoth.grammar.MammothBaseVisitor;
import org.mammoth.grammar.MammothParser;

public class AstBuilder extends MammothBaseVisitor<Object> {

    @Override
    public ProgramNode visitProgram(MammothParser.ProgramContext ctx) {
        ProgramNode program = new ProgramNode();
        if (ctx.packageDeclaration() != null) {
            program.setPackageName(visitPackageDeclaration(ctx.packageDeclaration()));
        }
        for (MammothParser.ImportDeclarationContext imp : ctx.importDeclaration()) {
            program.addImport(visitImportDeclaration(imp));
        }
        for (MammothParser.ClassDeclarationContext cls : ctx.classDeclaration()) {
            program.addClass(visitClassDeclaration(cls));
        }
        return program;
    }

    @Override
    public String visitPackageDeclaration(MammothParser.PackageDeclarationContext ctx) {
        return ctx.qualifiedName().getText();
    }

    @Override
    public String visitImportDeclaration(MammothParser.ImportDeclarationContext ctx) {
        return ctx.qualifiedName().getText();
    }

    @Override
    public ClassNode visitClassDeclaration(MammothParser.ClassDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        ClassNode classNode = new ClassNode(ctx.start, name);
        if (ctx.visibility() != null) {
            classNode.setVisibility(ctx.visibility().getText());
        }
        for (MammothParser.ClassMemberContext member : ctx.classMember()) {
            if (member.fieldDeclaration() != null) {
                classNode.addField(visitFieldDeclaration(member.fieldDeclaration()));
            } else if (member.methodDeclaration() != null) {
                classNode.addMethod(visitMethodDeclaration(member.methodDeclaration()));
            }
        }
        return classNode;
    }

    @Override
    public FieldNode visitFieldDeclaration(MammothParser.FieldDeclarationContext ctx) {
        MammothParser.VariableDeclaratorContext decl = ctx.variableDeclarator();
        String varName = decl.VARIABLE().getText();
        FieldNode field = new FieldNode(ctx.start, varName);
        if (ctx.visibility() != null) {
            field.setVisibility(ctx.visibility().getText());
        }
        if (ctx.type() != null) {
            field.setType(visitType(ctx.type()));
        }
        if (decl.expression() != null) {
            field.setInitializer(parseExpression(decl.expression()));
        }
        return field;
    }

    @Override
    public MethodNode visitMethodDeclaration(MammothParser.MethodDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        MethodNode method = new MethodNode(ctx.start, name);
        if (ctx.visibility() != null) {
            method.setVisibility(ctx.visibility().getText());
        }
        method.setStatic(ctx.STATIC() != null);
        if (ctx.parameters() != null) {
            for (MammothParser.ParameterContext p : ctx.parameters().parameter()) {
                method.addParameter(visitParameter(p));
            }
        }
        if (ctx.returnType() != null && ctx.returnType().type() != null) {
            method.setReturnType(visitType(ctx.returnType().type()));
        } else {
            method.setReturnType(new TypeNode("void", false));
        }
        method.setBody(visitBlock(ctx.block()));
        return method;
    }

    @Override
    public ParameterNode visitParameter(MammothParser.ParameterContext ctx) {
        String name = ctx.VARIABLE().getText();
        ParameterNode param = new ParameterNode(ctx.start, name);
        if (ctx.type() != null) {
            param.setType(visitType(ctx.type()));
        }
        if (ctx.expression() != null) {
            param.setDefaultValue(parseExpression(ctx.expression()));
        }
        return param;
    }

    @Override
    public TypeNode visitType(MammothParser.TypeContext ctx) {
        if (ctx.nullableType() != null) {
            String baseName = ctx.nullableType().primitiveType().typeName.getText();
            return new TypeNode(baseName, true);
        } else if (ctx.primitiveType() != null) {
            String baseName = ctx.primitiveType().typeName.getText();
            return new TypeNode(baseName, false);
        }
        return new TypeNode("void", false);
    }

    @Override
    public BlockNode visitBlock(MammothParser.BlockContext ctx) {
        BlockNode block = new BlockNode();
        for (MammothParser.StatementContext stmt : ctx.statement()) {
            block.addStatement((StatementNode) visit(stmt));
        }
        return block;
    }

    @Override
    public ExpressionStatementNode visitExpressionStatement(MammothParser.ExpressionStatementContext ctx) {
        return new ExpressionStatementNode(parseExpression(ctx.expression()));
    }

    @Override
    public ReturnNode visitReturnStatement(MammothParser.ReturnStatementContext ctx) {
        ExpressionNode value = ctx.expression() != null ? parseExpression(ctx.expression()) : null;
        return new ReturnNode(ctx.start, value);
    }

    @Override
    public LocalVarNode visitLocalVarDeclaration(MammothParser.LocalVarDeclarationContext ctx) {
        MammothParser.VariableDeclaratorContext decl = ctx.variableDeclarator();
        String varName = decl.VARIABLE().getText();
        LocalVarNode localVar = new LocalVarNode(ctx.start, varName);
        localVar.setType(visitType(ctx.type()));
        if (decl.expression() != null) {
            localVar.setInitializer(parseExpression(decl.expression()));
        }
        return localVar;
    }

    // ---- Label methods for expression rule ----

    @Override
    public ExpressionNode visitPrimaryExpr(MammothParser.PrimaryExprContext ctx) {
        return visitPrimary(ctx.primary());
    }

    @Override
    public ExpressionNode visitCastExpr(MammothParser.CastExprContext ctx) {
        TypeNode targetType = visitType(ctx.type());
        ExpressionNode expr = parseExpression(ctx.expression());
        return new CastNode(ctx.start, targetType, expr);
    }

    @Override
    public ExpressionNode visitUnaryMinusExpr(MammothParser.UnaryMinusExprContext ctx) {
        LiteralNode zero = new LiteralNode(ctx.start, 0, "int");
        ExpressionNode expr = parseExpression(ctx.expression());
        return new BinaryOpNode(ctx.start, zero, "-", expr);
    }

    @Override
    public ExpressionNode visitMultiplicativeExpr(MammothParser.MultiplicativeExprContext ctx) {
        ExpressionNode left = parseExpression(ctx.expression(0));
        ExpressionNode right = parseExpression(ctx.expression(1));
        return new BinaryOpNode(ctx.start, left, ctx.op.getText(), right);
    }

    @Override
    public ExpressionNode visitAdditiveExpr(MammothParser.AdditiveExprContext ctx) {
        ExpressionNode left = parseExpression(ctx.expression(0));
        ExpressionNode right = parseExpression(ctx.expression(1));
        return new BinaryOpNode(ctx.start, left, ctx.op.getText(), right);
    }

    @Override
    public ExpressionNode visitAssignmentExpr(MammothParser.AssignmentExprContext ctx) {
        Token varToken = ctx.VARIABLE().getSymbol();
        VariableNode target = new VariableNode(varToken, ctx.VARIABLE().getText());
        ExpressionNode value = parseExpression(ctx.expression());
        return new AssignmentNode(ctx.start, target, value);
    }

    // ---- Helper for expression dispatching ----

    @SuppressWarnings("unchecked")
    private ExpressionNode parseExpression(MammothParser.ExpressionContext ctx) {
        return (ExpressionNode) visit(ctx);
    }

    @Override
    public ExpressionNode visitPrimary(MammothParser.PrimaryContext ctx) {
        if (ctx.literal() != null) {
            return visitLiteral(ctx.literal());
        }
        if (ctx.closureExpression() != null) {
            return visitClosureExpression(ctx.closureExpression());
        }
        if (ctx.callExpression() != null) {
            return visitCallExpression(ctx.callExpression());
        }
        if (ctx.VARIABLE() != null) {
            return new VariableNode(ctx.VARIABLE().getSymbol(), ctx.VARIABLE().getText());
        }
        if (ctx.expression() != null) {
            return parseExpression(ctx.expression());
        }
        throw new RuntimeException("Unknown primary expression");
    }

    @Override
    public MethodCallNode visitCallExpression(MammothParser.CallExpressionContext ctx) {
        String name;
        if (ctx.identifier() != null) {
            name = ctx.identifier().getText();
        } else if (ctx.builtinPrint() != null) {
            name = ctx.builtinPrint().getText();
        } else {
            name = ctx.VARIABLE().getText();
        }
        MethodCallNode call = new MethodCallNode(ctx.start, name);
        if (ctx.builtinPrint() != null) {
            call.setBuiltinPrint(true);
        }
        if (ctx.arguments() != null) {
            for (MammothParser.ExpressionContext expr : ctx.arguments().expression()) {
                call.addArgument(parseExpression(expr));
            }
        }
        return call;
    }

    @Override
    public LiteralNode visitLiteral(MammothParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            String value = text.substring(1, text.length() - 1);
            return new LiteralNode(ctx.start, value, "string");
        }
        if (ctx.INTEGER_LITERAL() != null) {
            String text = ctx.INTEGER_LITERAL().getText();
            long value;
            if (text.startsWith("0x") || text.startsWith("0X")) {
                value = Long.parseLong(text.substring(2), 16);
            } else if (text.startsWith("0b") || text.startsWith("0B")) {
                value = Long.parseLong(text.substring(2), 2);
            } else {
                value = Long.parseLong(text);
            }
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return new LiteralNode(ctx.start, (int) value, "int");
            }
            return new LiteralNode(ctx.start, value, "int");
        }
        if (ctx.FLOAT_LITERAL() != null) {
            double value = Double.parseDouble(ctx.FLOAT_LITERAL().getText());
            return new LiteralNode(ctx.start, value, "float");
        }
        if (ctx.BOOLEAN_LITERAL() != null) {
            boolean value = Boolean.parseBoolean(ctx.BOOLEAN_LITERAL().getText());
            return new LiteralNode(ctx.start, value, "boolean");
        }
        if (ctx.NULL() != null) {
            return new LiteralNode(ctx.start, null, "null");
        }
        throw new RuntimeException("Unknown literal");
    }

    @Override
    public ClosureNode visitClosureExpression(MammothParser.ClosureExpressionContext ctx) {
        ClosureNode closure = new ClosureNode(ctx.start);

        if (ctx.parameters() != null) {
            for (MammothParser.ParameterContext p : ctx.parameters().parameter()) {
                closure.addParameter(visitParameter(p));
            }
        }

        if (ctx.captureClause() != null) {
            for (MammothParser.CaptureItemContext ci : ctx.captureClause().captureList().captureItem()) {
                String varName = ci.VARIABLE().getText();
                boolean byRef = ci.REF() != null;
                closure.addCapture(new CaptureItem(varName, byRef));
            }
        }

        if (ctx.returnType() != null && ctx.returnType().type() != null) {
            closure.setReturnType(visitType(ctx.returnType().type()));
        } else {
            closure.setReturnType(new TypeNode("void", false));
        }

        closure.setBody(visitBlock(ctx.block()));
        return closure;
    }
}

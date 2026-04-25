package org.mammoth.compiler;

import org.antlr.v4.runtime.Token;
import org.mammoth.compiler.ast.*;
import org.mammoth.grammar.MammothBaseVisitor;
import org.mammoth.grammar.MammothParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        for (MammothParser.AnnotationUsageContext annCtx : ctx.annotationUsage()) {
            classNode.addAnnotation(visitAnnotationUsage(annCtx));
        }
        if (ctx.visibility() != null) classNode.setVisibility(ctx.visibility().getText());

        // Check if annotation class (first alternative)
        classNode.setAnnotation(ctx.ANNOTATION() != null);

        // Check if enum class
        classNode.setEnum(ctx.ENUM() != null);

        if (classNode.isEnum()) {
            // Enum class
            MammothParser.EnumConstantListContext ecl = ctx.enumConstantList();
            for (org.antlr.v4.runtime.tree.TerminalNode id : ecl.IDENTIFIER()) {
                classNode.addEnumConstant(id.getText());
            }
            // Handle class members in enum (methods etc.)
            for (MammothParser.ClassMemberContext member : ecl.classMember()) {
                if (member.fieldDeclaration() != null) {
                    classNode.addField(visitFieldDeclaration(member.fieldDeclaration()));
                } else if (member.methodDeclaration() != null) {
                    classNode.addMethod(visitMethodDeclaration(member.methodDeclaration()));
                }
            }
        } else if (classNode.isAnnotation()) {
            // Annotation class: members come from annotationBody
            if (ctx.annotationBody() != null) {
                for (MammothParser.AnnotationMemberContext am : ctx.annotationBody().annotationMember()) {
                    String memberName = am.VARIABLE().getText();
                    FieldNode field = new FieldNode(am.start, memberName);
                    if (am.visibility() != null) field.setVisibility(am.visibility().getText());
                    if (am.type() != null) field.setType(visitType(am.type()));
                    if (am.expression() != null) field.setInitializer(parseExpression(am.expression()));
                    classNode.addField(field);
                }
            }
        } else {
            // Regular class: members from classMember*
            for (MammothParser.ClassMemberContext member : ctx.classMember()) {
                if (member.fieldDeclaration() != null) {
                    classNode.addField(visitFieldDeclaration(member.fieldDeclaration()));
                } else if (member.methodDeclaration() != null) {
                    classNode.addMethod(visitMethodDeclaration(member.methodDeclaration()));
                }
            }
        }
        return classNode;
    }

    @Override
    public FieldNode visitFieldDeclaration(MammothParser.FieldDeclarationContext ctx) {
        MammothParser.VariableDeclaratorContext decl = ctx.variableDeclarator();
        String varName = decl.VARIABLE().getText();
        FieldNode field = new FieldNode(ctx.start, varName);

        for (MammothParser.AnnotationUsageContext annCtx : ctx.annotationUsage()) {
            field.addAnnotation(visitAnnotationUsage(annCtx));
        }
        if (ctx.visibility() != null) field.setVisibility(ctx.visibility().getText());
        if (ctx.type() != null) field.setType(visitType(ctx.type()));
        if (decl.expression() != null) field.setInitializer(parseExpression(decl.expression()));
        return field;
    }

    @Override
    public MethodNode visitMethodDeclaration(MammothParser.MethodDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        MethodNode method = new MethodNode(ctx.start, name);
        for (MammothParser.AnnotationUsageContext annCtx : ctx.annotationUsage()) {
            method.addAnnotation(visitAnnotationUsage(annCtx));
        }
        if (ctx.visibility() != null) method.setVisibility(ctx.visibility().getText());
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
        for (MammothParser.AnnotationUsageContext annCtx : ctx.annotationUsage()) {
            param.addAnnotation(visitAnnotationUsage(annCtx));
        }
        if (ctx.type() != null) param.setType(visitType(ctx.type()));
        if (ctx.expression() != null) param.setDefaultValue(parseExpression(ctx.expression()));
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

    // ---- Control flow visitors ----

    @Override
    public IfNode visitIfStatement(MammothParser.IfStatementContext ctx) {
        ExpressionNode condition = parseExpression(ctx.expression());
        StatementNode thenBranch = (StatementNode) visit(ctx.statement(0));
        StatementNode elseBranch = ctx.ELSE() != null ? (StatementNode) visit(ctx.statement(1)) : null;
        return new IfNode(ctx.start, condition, thenBranch, elseBranch);
    }

    @Override
    public WhileNode visitWhileStatement(MammothParser.WhileStatementContext ctx) {
        return new WhileNode(ctx.start, parseExpression(ctx.expression()), (StatementNode) visit(ctx.statement()));
    }

    @Override
    public DoWhileNode visitDoWhileStatement(MammothParser.DoWhileStatementContext ctx) {
        return new DoWhileNode(ctx.start, parseExpression(ctx.expression()), (StatementNode) visit(ctx.statement()));
    }

    @Override
    public ForNode visitForStatement(MammothParser.ForStatementContext ctx) {
        LocalVarNode init = null;
        ExpressionNode initExpr = null;
        if (ctx.forInit() != null) {
            if (ctx.forInit().type() != null) {
                String varName = ctx.forInit().variableDeclarator().VARIABLE().getText();
                init = new LocalVarNode(ctx.start, varName);
                init.setType(visitType(ctx.forInit().type()));
                if (ctx.forInit().variableDeclarator().expression() != null) {
                    init.setInitializer(parseExpression(ctx.forInit().variableDeclarator().expression()));
                }
            } else if (ctx.forInit().expression() != null && !ctx.forInit().expression().isEmpty()) {
                initExpr = parseExpression(ctx.forInit().expression(0));
            }
        }
        ExpressionNode condition = ctx.expression() != null ? parseExpression(ctx.expression()) : null;
        ExpressionNode update = ctx.forUpdate() != null && !ctx.forUpdate().expression().isEmpty()
            ? parseExpression(ctx.forUpdate().expression(0)) : null;
        StatementNode body = (StatementNode) visit(ctx.statement());
        return new ForNode(ctx.start, init != null ? init : (initExpr != null ? new ExpressionStatementNode(initExpr) : null),
            condition, update, body);
    }

    @Override
    public ForEachNode visitForEachStatement(MammothParser.ForEachStatementContext ctx) {
        ExpressionNode iterable = parseExpression(ctx.expression());
        String valueVar = ctx.VARIABLE(0).getText();
        String keyVar = ctx.VARIABLE().size() > 1 ? ctx.VARIABLE(1).getText() : null;
        StatementNode body = (StatementNode) visit(ctx.statement());
        return new ForEachNode(ctx.start, iterable, keyVar, valueVar, body);
    }

    @Override
    public BreakNode visitBreakStatement(MammothParser.BreakStatementContext ctx) {
        return new BreakNode(ctx.start);
    }

    @Override
    public ContinueNode visitContinueStatement(MammothParser.ContinueStatementContext ctx) {
        return new ContinueNode(ctx.start);
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
    public ExpressionNode visitUnaryExpr(MammothParser.UnaryExprContext ctx) {
        ExpressionNode expr = parseExpression(ctx.expression());
        if (ctx.MINUS() != null) {
            LiteralNode zero = new LiteralNode(ctx.start, 0, "int");
            return new BinaryOpNode(ctx.start, zero, "-", expr);
        } else {
            return new BinaryOpNode(ctx.start, expr, "!", null);
        }
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

    @Override
    public ExpressionNode visitComparisonExpr(MammothParser.ComparisonExprContext ctx) {
        return new BinaryOpNode(ctx.start,
            parseExpression(ctx.expression(0)), ctx.op.getText(), parseExpression(ctx.expression(1)));
    }

    @Override
    public ExpressionNode visitEqualityExpr(MammothParser.EqualityExprContext ctx) {
        return new BinaryOpNode(ctx.start,
            parseExpression(ctx.expression(0)), ctx.op.getText(), parseExpression(ctx.expression(1)));
    }

    @Override
    public ExpressionNode visitAndExpr(MammothParser.AndExprContext ctx) {
        return new BinaryOpNode(ctx.start,
            parseExpression(ctx.expression(0)), "&&", parseExpression(ctx.expression(1)));
    }

    @Override
    public ExpressionNode visitOrExpr(MammothParser.OrExprContext ctx) {
        return new BinaryOpNode(ctx.start,
            parseExpression(ctx.expression(0)), "||", parseExpression(ctx.expression(1)));
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
        if (ctx.arrowExpression() != null) {
            return visitArrowExpression(ctx.arrowExpression());
        }
        if (ctx.newExpression() != null) {
            return visitNewExpression(ctx.newExpression());
        }
        if (ctx.callExpression() != null) {
            return visitCallExpression(ctx.callExpression());
        }
        if (ctx.VARIABLE() != null) {
            return new VariableNode(ctx.VARIABLE().getSymbol(), ctx.VARIABLE().getText());
        }
        if (ctx.qualifiedName() != null) {
            return new VariableNode(ctx.qualifiedName().start, ctx.qualifiedName().getText());
        }
        if (ctx.IDENTIFIER() != null) {
            return new VariableNode(ctx.IDENTIFIER().getSymbol(), ctx.IDENTIFIER().getText());
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
            for (MammothParser.ArgumentContext argCtx : ctx.arguments().argument()) {
                if (argCtx instanceof MammothParser.NamedArgumentContext nac) {
                    call.addNamedArgument(nac.IDENTIFIER().getText(), parseExpression(nac.expression()));
                } else if (argCtx instanceof MammothParser.PositionalArgumentContext pac) {
                    call.addArgument(parseExpression(pac.expression()));
                }
            }
        }
        return call;
    }

    @Override
    public ExpressionNode visitLiteral(MammothParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            String value = text.substring(1, text.length() - 1);

            // String interpolation: "Hello $name, age: $age"
            if (value.contains("$")) {
                return buildInterpolatedString(ctx.start, value);
            }

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
    public ClosureNode visitArrowExpression(MammothParser.ArrowExpressionContext ctx) {
        ClosureNode closure = new ClosureNode(ctx.start);
        closure.setArrowFunction(true);

        if (ctx.parameters() != null) {
            for (MammothParser.ParameterContext p : ctx.parameters().parameter()) {
                closure.addParameter(visitParameter(p));
            }
        }

        // Wrap expression body in a block with return
        BlockNode body = new BlockNode();
        body.addStatement(new ReturnNode(ctx.start, parseExpression(ctx.expression())));
        closure.setBody(body);

        // Return type: let bytecode generator infer from expression body
        closure.setReturnType(null);

        return closure;
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

    @Override
    public NewNode visitNewExpression(MammothParser.NewExpressionContext ctx) {
        String className = ctx.qualifiedName().getText();
        NewNode node = new NewNode(ctx.start, className);
        if (ctx.arguments() != null) {
            for (MammothParser.ArgumentContext argCtx : ctx.arguments().argument()) {
                if (argCtx instanceof MammothParser.NamedArgumentContext nac) {
                    // Named args not supported for new expressions, treat as positional
                    node.addArgument(parseExpression(nac.expression()));
                } else if (argCtx instanceof MammothParser.PositionalArgumentContext pac) {
                    node.addArgument(parseExpression(pac.expression()));
                }
            }
        }
        return node;
    }

    private static final Pattern VAR_IN_STR = Pattern.compile("\\$[a-zA-Z_][a-zA-Z_0-9]*");

    private ExpressionNode buildInterpolatedString(Token token, String s) {
        Matcher m = VAR_IN_STR.matcher(s);
        List<ExpressionNode> parts = new ArrayList<>();
        int lastEnd = 0;
        while (m.find()) {
            String before = s.substring(lastEnd, m.start());
            if (!before.isEmpty()) {
                parts.add(new LiteralNode(token, before, "string"));
            }
            parts.add(new VariableNode(token, m.group()));
            lastEnd = m.end();
        }
        if (lastEnd < s.length()) {
            parts.add(new LiteralNode(token, s.substring(lastEnd), "string"));
        }
        if (parts.isEmpty()) {
            return new LiteralNode(token, "", "string");
        }
        // Build left-associative concatenation tree
        ExpressionNode result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = new BinaryOpNode(token, result, "+", parts.get(i));
        }
        return result;
    }

    @Override
    public AnnotationNode visitAnnotationUsage(MammothParser.AnnotationUsageContext ctx) {
        String typeName = ctx.qualifiedName().getText();
        AnnotationNode ann = new AnnotationNode(ctx.start, typeName);

        if (ctx.annotationTarget() != null) {
            ann.setTarget(ctx.annotationTarget().getText());
        }

        if (ctx.annotationArgs() != null) {
            for (MammothParser.AnnotationArgContext argCtx : ctx.annotationArgs().annotationArg()) {
                String argName = argCtx.IDENTIFIER() != null ? argCtx.IDENTIFIER().getText() : null;
                ExpressionNode value = parseExpression(argCtx.expression());
                ann.addArg(new AnnotationNode.AnnotationArg(argName, value));
            }
        }

        return ann;
    }
}

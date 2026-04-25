package org.mammoth.compiler.codegen;

import org.mammoth.compiler.ast.*;
import org.mammoth.compiler.semantic.SemanticAnalyzer;
import org.mammoth.compiler.semantic.Symbol;
import org.mammoth.compiler.semantic.SymbolTable;
import org.mammoth.compiler.types.MammothType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

public class BytecodeGenerator {
    private final SemanticAnalyzer analyzer;
    private String currentClassName;
    private String currentClassInternalName;
    private final Map<ClosureNode, int[]> closureRegistry = new LinkedHashMap<>();
    private final Map<ClosureNode, String> closureInterfaceNames = new HashMap<>();
    private int closureCounter;

    public BytecodeGenerator(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public Map<String, byte[]> generate(ProgramNode program) {
        Map<String, byte[]> classFiles = new LinkedHashMap<>();
        String packageName = program.getPackageName();

        for (ClassNode cls : program.getClasses()) {
            String packagePrefix = (packageName != null && !packageName.isEmpty())
                ? packageName.replace('.', '/') + "/" : "";
            currentClassName = cls.getName();
            currentClassInternalName = packagePrefix + cls.getName();
            closureRegistry.clear();
            closureCounter = 0;

            String classFileName = currentClassInternalName + ".class";
            byte[] bytes = generateClass(program, cls, packageName);
            classFiles.put(classFileName, bytes);

            // Generate closure classes
            for (Map.Entry<ClosureNode, int[]> entry : closureRegistry.entrySet()) {
                int[] ids = entry.getValue();
                String baseInternalName = currentClassInternalName + "$Closure$" + ids[0];
                Map<String, byte[]> closureClasses = generateClosureBundle(entry.getKey(), baseInternalName);
                classFiles.putAll(closureClasses);
                // Store the interface name in the ID array for call-site lookup
                ids = new int[]{ids[0]}; // keep id
            }
        }

        return classFiles;
    }

    private byte[] generateClass(ProgramNode program, ClassNode cls, String packageName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        int classAccess = getClassAccess(cls.getVisibility());
        String[] interfaces = {};
        cw.visit(Opcodes.V21, classAccess, currentClassInternalName, null, "java/lang/Object", interfaces);

        for (FieldNode field : cls.getFields()) {
            generateField(cw, field);
        }

        generateDefaultConstructor(cw);

        for (MethodNode method : cls.getMethods()) {
            generateMethod(cw, method, cls);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateField(ClassWriter cw, FieldNode field) {
        int access = getFieldAccess(field.getVisibility());
        MammothType type = getFieldType(field);
        String descriptor = type.getDescriptor();
        cw.visitField(access, stripDollar(field.getName()), descriptor, null, null).visitEnd();
    }

    private void generateDefaultConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void generateMethod(ClassWriter cw, MethodNode method, ClassNode cls) {
        boolean isMain = method.getName().equals("main") && method.isStatic();
        int access = getMethodAccess(method.getVisibility());

        if (method.isStatic()) access |= Opcodes.ACC_STATIC;

        MammothType returnType = getReturnType(method);
        String descriptor;

        if (isMain) {
            descriptor = "([Ljava/lang/String;)V";
            access |= Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        } else {
            descriptor = buildMethodDescriptor(method);
        }

        MethodVisitor mv = cw.visitMethod(access, method.getName(), descriptor, null, null);
        mv.visitCode();

        SymbolTable symbolTable = analyzer.getSymbolTable();

        if (method.getBody() != null) {
            generateBlock(mv, method.getBody(), symbolTable);
        }

        if (returnType == MammothType.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }

        mv.visitMaxs(10, 20);
        mv.visitEnd();
    }

    private int registerClosure(ClosureNode closure) {
        return closureRegistry.computeIfAbsent(closure, k -> new int[]{closureCounter++})[0];
    }

    // ======================== Statement generation ========================

    private void generateBlock(MethodVisitor mv, BlockNode block, SymbolTable symbolTable) {
        for (StatementNode stmt : block.getStatements()) {
            generateStatement(mv, stmt, symbolTable);
        }
    }

    private void generateStatement(MethodVisitor mv, StatementNode stmt, SymbolTable symbolTable) {
        if (stmt instanceof ExpressionStatementNode es) {
            generateExpression(mv, es.getExpression(), symbolTable);
            ExpressionNode expr = es.getExpression();
            if (!(expr instanceof MethodCallNode mcn && mcn.isBuiltinPrint())) {
                MammothType type = getExprType(expr);
                if (type != null && type != MammothType.VOID) {
                    if (type == MammothType.INT64 || type == MammothType.FLOAT64) {
                        mv.visitInsn(Opcodes.POP2);
                    } else {
                        mv.visitInsn(Opcodes.POP);
                    }
                }
            }
        } else if (stmt instanceof ReturnNode rn) {
            if (rn.hasValue()) {
                generateExpression(mv, rn.getValue(), symbolTable);
                MammothType type = getExprType(rn.getValue());
                mv.visitInsn(type.getReturnOpcode());
            } else {
                mv.visitInsn(Opcodes.RETURN);
            }
        } else if (stmt instanceof BlockNode bn) {
            generateBlock(mv, bn, symbolTable);
        } else if (stmt instanceof LocalVarNode lvn) {
            generateLocalVar(mv, lvn, symbolTable);
        }
    }

    private void generateLocalVar(MethodVisitor mv, LocalVarNode lvn, SymbolTable symbolTable) {
        if (lvn.getInitializer() != null) {
            generateExpression(mv, lvn.getInitializer(), symbolTable);
            MammothType targetType = lvn.getResolvedType();
            if (targetType != null) {
                MammothType valueType = getExprType(lvn.getInitializer());
                emitCast(mv, valueType, targetType);
                mv.visitVarInsn(targetType.getStoreOpcode(), lvn.getLocalIndex());
            }
        }
    }

    // ======================== Expression generation ========================

    private void generateExpression(MethodVisitor mv, ExpressionNode expr, SymbolTable symbolTable) {
        if (expr instanceof LiteralNode ln) {
            generateLiteral(mv, ln);
        } else if (expr instanceof VariableNode vn) {
            generateVariableLoad(mv, vn, symbolTable);
        } else if (expr instanceof AssignmentNode an) {
            generateAssignment(mv, an, symbolTable);
        } else if (expr instanceof MethodCallNode mcn) {
            generateMethodCall(mv, mcn, symbolTable);
        } else if (expr instanceof BinaryOpNode bon) {
            generateBinaryOp(mv, bon, symbolTable);
        } else if (expr instanceof CastNode cn) {
            generateCast(mv, cn, symbolTable);
        } else if (expr instanceof ClosureNode cn) {
            generateClosureExpr(mv, cn, symbolTable);
        }
    }

    private void generateClosureExpr(MethodVisitor mv, ClosureNode closure, SymbolTable symbolTable) {
        int id = registerClosure(closure);
        String implInternalName = currentClassInternalName + "$Closure$" + id;
        String fnInterfaceName = implInternalName + "$Fn";

        closureInterfaceNames.put(closure, fnInterfaceName);

        // NEW, DUP (object ref on stack first)
        mv.visitTypeInsn(Opcodes.NEW, implInternalName);
        mv.visitInsn(Opcodes.DUP);

        // Now load capture values onto stack (after DUP for constructor call order)
        List<CaptureItem> captures = closure.getCaptures();
        for (CaptureItem cap : captures) {
            if (cap.isByReference()) {
                // Reference capture: load the variable as a Ref (ALOAD)
                if (cap.getLocalIndex() >= 0) {
                    mv.visitVarInsn(Opcodes.ALOAD, cap.getLocalIndex());
                }
            } else if (cap.getResolvedType() instanceof MammothType mt && cap.getLocalIndex() >= 0) {
                mv.visitVarInsn(mt.getLoadOpcode(), cap.getLocalIndex());
            }
        }

        // Constructor call: objref, captures...
        StringBuilder ctorDesc = new StringBuilder("(");
        for (CaptureItem cap : captures) {
            if (cap.isByReference()) {
                ctorDesc.append("Lorg/mammoth/stdlib/Ref;");
            } else if (cap.getResolvedType() instanceof MammothType mt) {
                ctorDesc.append(mt.getDescriptor());
            }
        }
        ctorDesc.append(")V");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, implInternalName, "<init>", ctorDesc.toString(), false);
    }

    // ======================== Literal helpers ========================

    private void generateLiteral(MethodVisitor mv, LiteralNode ln) {
        Object value = ln.getValue();
        String typeHint = ln.getTypeHint();

        if (value == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (typeHint.equals("string")) {
            mv.visitLdcInsn(value);
        } else if (typeHint.equals("boolean")) {
            mv.visitInsn((boolean) value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        } else if (typeHint.equals("int")) {
            if (value instanceof Integer iv) {
                pushInt(mv, iv);
            } else if (value instanceof Long lv) {
                pushLong(mv, lv);
            }
        } else if (typeHint.equals("float")) {
            double dv = (double) value;
            mv.visitLdcInsn(dv);
        }
    }

    private void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private void pushLong(MethodVisitor mv, long value) {
        if (value == 0) {
            mv.visitInsn(Opcodes.LCONST_0);
        } else if (value == 1) {
            mv.visitInsn(Opcodes.LCONST_1);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    // ======================== Variable load ========================

    private void generateVariableLoad(MethodVisitor mv, VariableNode vn, SymbolTable symbolTable) {
        if (vn.getResolvedType() != null && vn.getLocalIndex() >= 0) {
            mv.visitVarInsn(vn.getResolvedType().getLoadOpcode(), vn.getLocalIndex());
        }
    }

    // ======================== Assignment ========================

    private void generateAssignment(MethodVisitor mv, AssignmentNode an, SymbolTable symbolTable) {
        VariableNode target = an.getTarget();
        generateExpression(mv, an.getValue(), symbolTable);
        if (target.getResolvedType() != null && target.getLocalIndex() >= 0) {
            MammothType targetType = target.getResolvedType();
            if (targetType == MammothType.INT64 || targetType == MammothType.FLOAT64) {
                mv.visitInsn(Opcodes.DUP2);
            } else {
                mv.visitInsn(Opcodes.DUP);
            }
            mv.visitVarInsn(targetType.getStoreOpcode(), target.getLocalIndex());
        }
    }

    // ======================== Method call ========================

    private void generateMethodCall(MethodVisitor mv, MethodCallNode mcn, SymbolTable symbolTable) {
        String methodName = mcn.getMethodName();

        if (mcn.isBuiltinPrint()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            List<ExpressionNode> args = mcn.getArguments();
            if (args.size() == 1) {
                generateExpression(mv, args.get(0), symbolTable);
                MammothType argType = getExprType(args.get(0));
                String argDesc = argType != null ? argType.getDescriptor() : "Ljava/lang/Object;";
                String printDesc = "(" + argDesc + ")V";
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", methodName, printDesc, false);
            } else if (args.isEmpty()) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", methodName, "()V", false);
            }
            return;
        }

        // Variable call: $fn(args)
        if (mcn.isVariableCall()) {
            int targetIdx = mcn.getTargetLocalIndex();
            mv.visitVarInsn(Opcodes.ALOAD, targetIdx);

            // Find the closure interface name (use first available)
            String ifaceName = null;
            ClosureNode targetClosure = null;
            for (Map.Entry<ClosureNode, String> entry : closureInterfaceNames.entrySet()) {
                ifaceName = entry.getValue();
                targetClosure = entry.getKey();
                break;
            }

            if (ifaceName != null && targetClosure != null) {
                // Build invoke descriptor from the closure's parameter/return types
                StringBuilder invokeDesc = new StringBuilder("(");
                for (int i = 0; i < mcn.getArguments().size(); i++) {
                    ExpressionNode arg = mcn.getArguments().get(i);
                    MammothType expectedType = i < targetClosure.getParameters().size()
                        && targetClosure.getParameters().get(i).getType() != null
                        ? MammothType.fromTypeName(targetClosure.getParameters().get(i).getType().getBaseTypeName())
                        : null;
                    generateExpression(mv, arg, symbolTable);
                    if (expectedType != null) {
                        MammothType actualType = getExprType(arg);
                        emitCast(mv, actualType, expectedType);
                        invokeDesc.append(expectedType.getDescriptor());
                    } else {
                        invokeDesc.append("Ljava/lang/Object;");
                    }
                }
                invokeDesc.append(")");
                MammothType retType = targetClosure.getReturnType() != null
                    ? MammothType.fromTypeName(targetClosure.getReturnType().getBaseTypeName())
                    : MammothType.VOID;
                invokeDesc.append(retType.getDescriptor());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ifaceName, "invoke", invokeDesc.toString(), true);
            }
            return;
        }

        // Static method call
        for (ExpressionNode arg : mcn.getArguments()) {
            generateExpression(mv, arg, symbolTable);
        }
        StringBuilder desc = new StringBuilder("(");
        for (ExpressionNode arg : mcn.getArguments()) {
            MammothType type = getExprType(arg);
            desc.append(type != null ? type.getDescriptor() : "Ljava/lang/Object;");
        }
        desc.append(")V");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, currentClassInternalName, methodName, desc.toString(), false);
    }

    // ======================== Binary ops ========================

    private void generateBinaryOp(MethodVisitor mv, BinaryOpNode bon, SymbolTable symbolTable) {
        generateExpression(mv, bon.getLeft(), symbolTable);
        generateExpression(mv, bon.getRight(), symbolTable);

        MammothType leftType = getExprType(bon.getLeft());
        String op = bon.getOp();

        if (leftType == MammothType.STRING && op.equals("+")) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }

        int opcode = switch (leftType) {
            case INT64 -> switch (op) {
                case "+" -> Opcodes.LADD;
                case "-" -> Opcodes.LSUB;
                case "*" -> Opcodes.LMUL;
                case "/" -> Opcodes.LDIV;
                case "%" -> Opcodes.LREM;
                default -> throw new RuntimeException("Unknown op: " + op);
            };
            case FLOAT32 -> switch (op) {
                case "+" -> Opcodes.FADD;
                case "-" -> Opcodes.FSUB;
                case "*" -> Opcodes.FMUL;
                case "/" -> Opcodes.FDIV;
                case "%" -> Opcodes.FREM;
                default -> throw new RuntimeException("Unknown op: " + op);
            };
            case FLOAT64 -> switch (op) {
                case "+" -> Opcodes.DADD;
                case "-" -> Opcodes.DSUB;
                case "*" -> Opcodes.DMUL;
                case "/" -> Opcodes.DDIV;
                case "%" -> Opcodes.DREM;
                default -> throw new RuntimeException("Unknown op: " + op);
            };
            default -> switch (op) {
                case "+" -> Opcodes.IADD;
                case "-" -> Opcodes.ISUB;
                case "*" -> Opcodes.IMUL;
                case "/" -> Opcodes.IDIV;
                case "%" -> Opcodes.IREM;
                default -> throw new RuntimeException("Unknown op: " + op);
            };
        };
        mv.visitInsn(opcode);
    }

    // ======================== Cast ========================

    private void generateCast(MethodVisitor mv, CastNode cn, SymbolTable symbolTable) {
        generateExpression(mv, cn.getExpression(), symbolTable);
        MammothType fromType = getExprType(cn.getExpression());
        MammothType toType = analyzer.resolveType(cn.getTargetType());
        emitCast(mv, fromType, toType);
    }

    private void emitCast(MethodVisitor mv, MammothType fromType, MammothType toType) {
        if (fromType == null || toType == null || fromType == toType) return;
        String castOp = toType.getCastOpcode(fromType);
        if (castOp != null) {
            switch (castOp) {
                case "I2L" -> mv.visitInsn(Opcodes.I2L);
                case "I2F" -> mv.visitInsn(Opcodes.I2F);
                case "I2D" -> mv.visitInsn(Opcodes.I2D);
                case "L2I" -> mv.visitInsn(Opcodes.L2I);
                case "L2F" -> mv.visitInsn(Opcodes.L2F);
                case "L2D" -> mv.visitInsn(Opcodes.L2D);
                case "F2I" -> mv.visitInsn(Opcodes.F2I);
                case "F2L" -> mv.visitInsn(Opcodes.F2L);
                case "F2D" -> mv.visitInsn(Opcodes.F2D);
                case "D2I" -> mv.visitInsn(Opcodes.D2I);
                case "D2L" -> mv.visitInsn(Opcodes.D2L);
                case "D2F" -> mv.visitInsn(Opcodes.D2F);
            }
        }
        if (fromType.isIntegral() && (toType == MammothType.INT8 || toType == MammothType.INT16)) {
            if (toType == MammothType.INT8) mv.visitInsn(Opcodes.I2B);
            if (toType == MammothType.INT16) mv.visitInsn(Opcodes.I2S);
        }
    }

    // ======================== Closure class generation ========================

    private Map<String, byte[]> generateClosureBundle(ClosureNode closure, String baseInternalName) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String fnInterfaceName = baseInternalName + "$Fn";
        String implName = baseInternalName;

        closureInterfaceNames.put(closure, fnInterfaceName);

        // Generate the functional interface
        result.put(fnInterfaceName + ".class", generateClosureInterfaceClass(closure, fnInterfaceName));

        // Generate the implementation
        result.put(implName + ".class", generateClosureImplClass(closure, implName, fnInterfaceName));

        return result;
    }

    private byte[] generateClosureInterfaceClass(ClosureNode closure, String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            internalName, null, "java/lang/Object", null);

        StringBuilder methodDesc = new StringBuilder("(");
        for (ParameterNode param : closure.getParameters()) {
            if (param.getType() != null) {
                MammothType mt = MammothType.fromTypeName(param.getType().getBaseTypeName());
                methodDesc.append(mt.getDescriptor());
            } else {
                methodDesc.append("Ljava/lang/Object;");
            }
        }
        methodDesc.append(")");
        MammothType retType = closure.getReturnType() != null
            ? MammothType.fromTypeName(closure.getReturnType().getBaseTypeName())
            : MammothType.VOID;
        methodDesc.append(retType.getDescriptor());

        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "invoke", methodDesc.toString(), null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateClosureImplClass(ClosureNode closure, String internalName, String fnInterfaceName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String[] interfaces = {fnInterfaceName};
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
            "java/lang/Object", interfaces);

        List<CaptureItem> captures = closure.getCaptures();

        // Fields for captured values
        for (int i = 0; i < captures.size(); i++) {
            CaptureItem cap = captures.get(i);
            String fieldName = cap.getJvmName();
            String descriptor;
            if (cap.isByReference()) {
                descriptor = "Lorg/mammoth/stdlib/Ref;";
            } else {
                MammothType ft = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                descriptor = ft.getDescriptor();
            }
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldName,
                descriptor, null, null).visitEnd();
        }

        // Constructor with captured values
        generateClosureConstructor(cw, closure, internalName);

        // invoke method
        generateClosureInvokeMethod(cw, closure, internalName);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateClosureConstructor(ClassWriter cw, ClosureNode closure, String internalName) {
        List<CaptureItem> captures = closure.getCaptures();
        StringBuilder ctorDesc = new StringBuilder("(");
        for (CaptureItem cap : captures) {
            if (cap.isByReference()) {
                ctorDesc.append("Lorg/mammoth/stdlib/Ref;");
            } else {
                MammothType ft = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                ctorDesc.append(ft.getDescriptor());
            }
        }
        ctorDesc.append(")V");

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc.toString(), null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int slot = 1;
        for (CaptureItem cap : captures) {
            String fieldName = cap.getJvmName();
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            if (cap.isByReference()) {
                ctor.visitVarInsn(Opcodes.ALOAD, slot);
                ctor.visitFieldInsn(Opcodes.PUTFIELD, internalName, fieldName, "Lorg/mammoth/stdlib/Ref;");
                slot += 1;
            } else {
                MammothType ft = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                ctor.visitVarInsn(ft.getLoadOpcode(), slot);
                ctor.visitFieldInsn(Opcodes.PUTFIELD, internalName, fieldName, ft.getDescriptor());
                slot += (ft == MammothType.INT64 || ft == MammothType.FLOAT64) ? 2 : 1;
            }
        }
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(3, slot);
        ctor.visitEnd();
    }

    private void generateClosureInvokeMethod(ClassWriter cw, ClosureNode closure, String internalName) {
        StringBuilder methodDesc = new StringBuilder("(");
        for (ParameterNode param : closure.getParameters()) {
            if (param.getType() != null) {
                MammothType mt = MammothType.fromTypeName(param.getType().getBaseTypeName());
                methodDesc.append(mt.getDescriptor());
            } else {
                methodDesc.append("Ljava/lang/Object;");
            }
        }
        methodDesc.append(")");
        MammothType retType = closure.getReturnType() != null
            ? MammothType.fromTypeName(closure.getReturnType().getBaseTypeName())
            : MammothType.VOID;
        methodDesc.append(retType.getDescriptor());

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke", methodDesc.toString(), null, null);
        mv.visitCode();

        if (closure.getBody() != null) {
            visitClosureBody(mv, closure, internalName);
        }

        if (retType == MammothType.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(10, 20);
        mv.visitEnd();
    }

    private void visitClosureBody(MethodVisitor mv, ClosureNode closure, String internalName) {
        for (StatementNode stmt : closure.getBody().getStatements()) {
            if (stmt instanceof ExpressionStatementNode es) {
                ExpressionNode expr = es.getExpression();
                emitClosureExpr(mv, expr, closure, internalName);
                if (!(expr instanceof MethodCallNode mcn && mcn.isBuiltinPrint())) {
                    MammothType type = getExprType(expr);
                    if (type != null && type != MammothType.VOID) {
                        if (type == MammothType.INT64 || type == MammothType.FLOAT64) {
                            mv.visitInsn(Opcodes.POP2);
                        } else {
                            mv.visitInsn(Opcodes.POP);
                        }
                    }
                }
            } else if (stmt instanceof ReturnNode rn) {
                if (rn.hasValue()) {
                    emitClosureExpr(mv, rn.getValue(), closure, internalName);
                    MammothType type = getExprType(rn.getValue());
                    mv.visitInsn(type.getReturnOpcode());
                } else {
                    mv.visitInsn(Opcodes.RETURN);
                }
            } else if (stmt instanceof LocalVarNode lvn) {
                if (lvn.getInitializer() != null) {
                    emitClosureExpr(mv, lvn.getInitializer(), closure, internalName);
                    MammothType targetType = lvn.getResolvedType();
                    if (targetType != null) {
                        MammothType valueType = getExprType(lvn.getInitializer());
                        emitCast(mv, valueType, targetType);
                        mv.visitVarInsn(targetType.getStoreOpcode(), lvn.getLocalIndex() + 1); // +1 for 'this'
                    }
                }
            }
        }
    }

    private void emitClosureExpr(MethodVisitor mv, ExpressionNode expr, ClosureNode closure, String internalName) {
        if (expr instanceof LiteralNode ln) {
            generateLiteral(mv, ln);
        } else if (expr instanceof VariableNode vn) {
            String name = vn.getName();
            // Check if closure parameter
            int paramSlot = 1; // slot 0 is 'this' in instance methods
            for (int i = 0; i < closure.getParameters().size(); i++) {
                ParameterNode p = closure.getParameters().get(i);
                if (p.getName().equals(name)) {
                    MammothType pt = p.getType() != null
                        ? MammothType.fromTypeName(p.getType().getBaseTypeName())
                        : MammothType.STRING;
                    mv.visitVarInsn(pt.getLoadOpcode(), paramSlot);
                    return;
                }
                if (p.getType() != null) {
                    MammothType pt = MammothType.fromTypeName(p.getType().getBaseTypeName());
                    paramSlot += (pt == MammothType.INT64 || pt == MammothType.FLOAT64) ? 2 : 1;
                } else {
                    paramSlot++;
                }
            }
            // Check if captured variable (value capture or reference capture)
            for (CaptureItem cap : closure.getCaptures()) {
                if (cap.getVariableName().equals(name)) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    MammothType ct = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                    mv.visitFieldInsn(Opcodes.GETFIELD, internalName, cap.getJvmName(), ct.getDescriptor());
                    // For reference captures, unwrap Ref -> load Ref.value
                    if (cap.isByReference()) {
                        mv.visitFieldInsn(Opcodes.GETFIELD, "org/mammoth/stdlib/Ref", "value", "Ljava/lang/Object;");
                        // Unbox to expected type - for now, just return the Object ref
                    }
                    return;
                }
            }
        } else if (expr instanceof BinaryOpNode bon) {
            emitClosureExpr(mv, bon.getLeft(), closure, internalName);
            emitClosureExpr(mv, bon.getRight(), closure, internalName);
            MammothType leftType = getExprType(bon.getLeft());
            String op = bon.getOp();
            int opcode = switch (leftType) {
                case INT64 -> op.equals("+") ? Opcodes.LADD : op.equals("-") ? Opcodes.LSUB :
                             op.equals("*") ? Opcodes.LMUL : Opcodes.LDIV;
                case FLOAT64 -> op.equals("+") ? Opcodes.DADD : Opcodes.DSUB;
                default -> op.equals("+") ? Opcodes.IADD : Opcodes.ISUB;
            };
            mv.visitInsn(opcode);
        } else if (expr instanceof MethodCallNode mcn) {
            if (mcn.isBuiltinPrint()) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                List<ExpressionNode> args = mcn.getArguments();
                if (!args.isEmpty()) {
                    emitClosureExpr(mv, args.get(0), closure, internalName);
                    MammothType argType = getExprType(args.get(0));
                    String argDesc = argType != null ? argType.getDescriptor() : "Ljava/lang/Object;";
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", mcn.getMethodName(),
                        "(" + argDesc + ")V", false);
                }
            }
        } else if (expr instanceof AssignmentNode an) {
            // Assignment inside closure body - handles captured variable writes
            VariableNode target = an.getTarget();
            String targetName = target.getName();
            emitClosureExpr(mv, an.getValue(), closure, internalName);
            // Check if the target is a captured variable
            for (CaptureItem cap : closure.getCaptures()) {
                if (cap.getVariableName().equals(targetName)) {
                    if (cap.isByReference()) {
                        // Write through Ref: dup value, aload_0, getfield(cap), swap, putfield(Ref.value)
                        MammothType ct = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                        if (ct == MammothType.INT64 || ct == MammothType.FLOAT64) {
                            mv.visitInsn(Opcodes.DUP2);
                        } else {
                            mv.visitInsn(Opcodes.DUP);
                        }
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitFieldInsn(Opcodes.GETFIELD, internalName, cap.getJvmName(), "Lorg/mammoth/stdlib/Ref;");
                        mv.visitInsn(Opcodes.SWAP);
                        mv.visitFieldInsn(Opcodes.PUTFIELD, "org/mammoth/stdlib/Ref", "value", "Ljava/lang/Object;");
                    } else {
                        // Direct write to captured value field (effectively final - should error, but for now just write)
                        MammothType ct = cap.getResolvedType() instanceof MammothType mt ? mt : MammothType.STRING;
                        if (ct == MammothType.INT64 || ct == MammothType.FLOAT64) {
                            mv.visitInsn(Opcodes.DUP2);
                        } else {
                            mv.visitInsn(Opcodes.DUP);
                        }
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitInsn(Opcodes.SWAP);
                        mv.visitFieldInsn(Opcodes.PUTFIELD, internalName, cap.getJvmName(), ct.getDescriptor());
                    }
                    return;
                }
            }
        }
    }

    // ======================== Type helpers ========================

    private MammothType getReturnType(MethodNode method) {
        if (method.getReturnType() != null) {
            return MammothType.fromTypeName(method.getReturnType().getBaseTypeName());
        }
        return MammothType.VOID;
    }

    private MammothType getExprType(ExpressionNode expr) {
        if (expr instanceof LiteralNode ln) {
            return MammothType.inferFromLiteral(ln.getTypeHint());
        }
        if (expr instanceof VariableNode vn) {
            TypeNode inferred = vn.getInferredType();
            if (inferred != null) return MammothType.fromTypeName(inferred.getBaseTypeName());
            return MammothType.STRING;
        }
        if (expr instanceof BinaryOpNode bon) {
            return getExprType(bon.getLeft());
        }
        if (expr instanceof CastNode cn) {
            return MammothType.fromTypeName(cn.getTargetType().getBaseTypeName());
        }
        if (expr instanceof AssignmentNode an) {
            return getExprType(an.getValue());
        }
        if (expr instanceof MethodCallNode mcn) {
            if (mcn.isBuiltinPrint()) return MammothType.VOID;
            return MammothType.VOID;
        }
        if (expr instanceof ClosureNode) {
            return MammothType.STRING; // closure type = Object for now
        }
        return MammothType.STRING;
    }

    private MammothType getFieldType(FieldNode field) {
        if (field.getType() != null) {
            return MammothType.fromTypeName(field.getType().getBaseTypeName());
        }
        return MammothType.STRING;
    }

    private String buildMethodDescriptor(MethodNode method) {
        StringBuilder sb = new StringBuilder("(");
        for (ParameterNode param : method.getParameters()) {
            if (param.getType() != null) {
                MammothType mt = MammothType.fromTypeName(param.getType().getBaseTypeName());
                sb.append(mt.getDescriptor());
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
        sb.append(")");
        MammothType returnType = getReturnType(method);
        sb.append(returnType.getDescriptor());
        return sb.toString();
    }

    private int getClassAccess(String visibility) {
        return Opcodes.ACC_PUBLIC;
    }

    private int getFieldAccess(String visibility) {
        return switch (visibility) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "private" -> Opcodes.ACC_PRIVATE;
            default -> Opcodes.ACC_PUBLIC;
        };
    }

    private int getMethodAccess(String visibility) {
        return switch (visibility) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "private" -> Opcodes.ACC_PRIVATE;
            default -> Opcodes.ACC_PUBLIC;
        };
    }

    private String stripDollar(String name) {
        if (name.startsWith("$")) return name.substring(1);
        return name;
    }
}

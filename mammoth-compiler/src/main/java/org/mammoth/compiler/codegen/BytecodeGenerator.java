package org.mammoth.compiler.codegen;

import org.mammoth.compiler.ast.*;
import org.mammoth.compiler.semantic.SemanticAnalyzer;
import org.mammoth.compiler.semantic.Symbol;
import org.mammoth.compiler.semantic.SymbolTable;
import org.mammoth.compiler.types.MammothType;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

public class BytecodeGenerator {
    private final SemanticAnalyzer analyzer;
    private String currentClassName;
    private String currentClassInternalName;
    private ClassNode currentClassNode;
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
            currentClassNode = cls;
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
        if (cls.isEnum()) {
            classAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM | Opcodes.ACC_SUPER;
        } else if (cls.isInterface()) {
            classAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        } else if (cls.isAnnotation()) {
            classAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION;
        } else {
            if (cls.isFinal()) classAccess |= Opcodes.ACC_FINAL;
            if (cls.isAbstract()) classAccess |= Opcodes.ACC_ABSTRACT;
            classAccess |= Opcodes.ACC_SUPER;
        }
        String[] interfaces;
        if (cls.getInterfaces().isEmpty()) {
            interfaces = new String[0];
        } else {
            interfaces = new String[cls.getInterfaces().size()];
            for (int i = 0; i < cls.getInterfaces().size(); i++) {
                interfaces[i] = cls.getInterfaces().get(i).replace('.', '/');
            }
        }
        String superClass;
        if (cls.isEnum()) {
            superClass = "java/lang/Enum";
        } else if (cls.getParentClassName() != null && !cls.getParentClassName().isEmpty()) {
            superClass = cls.getParentClassName().replace('.', '/');
        } else {
            superClass = "java/lang/Object";
        }
        String signature = cls.isEnum() ? "Ljava/lang/Enum<L" + currentClassInternalName + ";>;" : null;
        cw.visit(Opcodes.V1_5, classAccess, currentClassInternalName, signature, superClass, interfaces);

        // Apply class-level annotations
        for (AnnotationNode ann : cls.getAnnotations()) {
            applyAnnotation(cw, ann);
        }

        if (cls.isEnum()) {
            generateEnumClass(cw, cls);
        } else if (cls.isInterface()) {
            for (FieldNode field : cls.getFields()) {
                // Interface constants: public static final
                int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
                MammothType type = getFieldType(field);
                var fv = cw.visitField(access, field.getName().startsWith("$") ? field.getName().substring(1) : field.getName(),
                    type.getDescriptor(), null, field.getInitializer() instanceof LiteralNode ln ? ln.getValue() : null);
                fv.visitEnd();
            }
            for (MethodNode method : cls.getMethods()) {
                generateInterfaceMethod(cw, method, cls);
            }
        } else {
            for (FieldNode field : cls.getFields()) {
                if (cls.isAnnotation()) {
                    generateAnnotationMember(cw, field);
                } else {
                    generateField(cw, field);
                }
            }

            // Only generate default constructor if no __construct method
            boolean hasConstructor = false;
            for (MethodNode m : cls.getMethods()) {
                if (m.isConstructor()) { hasConstructor = true; break; }
            }
            if (!cls.isAnnotation() && !cls.isAbstract() && !hasConstructor) {
                generateDefaultConstructor(cw);
            }

            for (MethodNode method : cls.getMethods()) {
                generateMethod(cw, method, cls);
            }
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateEnumClass(ClassWriter cw, ClassNode cls) {
        // Enum constant public static final fields
        List<String> constants = cls.getEnumConstants();
        String enumDesc = "L" + currentClassInternalName + ";";

        for (String constant : constants) {
            cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                constant, enumDesc, null, null).visitEnd();
        }

        // Synthetic $VALUES field
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
            "$VALUES", "[" + enumDesc, null, null).visitEnd();

        // Static initializer <clinit>
        generateEnumStaticInit(cw, cls);

        // Private constructor (String name, int ordinal)
        generateEnumConstructor(cw, cls);

        // values() method
        generateEnumValues(cw, cls);

        // valueOf(String) method
        generateEnumValueOf(cw, cls);

        // User-defined methods
        for (MethodNode method : cls.getMethods()) {
            generateMethod(cw, method, cls);
        }
    }

    private void generateEnumStaticInit(ClassWriter cw, ClassNode cls) {
        List<String> constants = cls.getEnumConstants();
        String enumDesc = "L" + currentClassInternalName + ";";
        String enumArrDesc = "[" + enumDesc;

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        // Create each enum constant
        for (int i = 0; i < constants.size(); i++) {
            String name = constants.get(i);
            mv.visitTypeInsn(Opcodes.NEW, currentClassInternalName);
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(name);
            pushInt(mv, i);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, currentClassInternalName, "<init>",
                "(Ljava/lang/String;I)V", false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, currentClassInternalName, name, enumDesc);
        }

        // Build $VALUES array
        pushInt(mv, constants.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, currentClassInternalName);
        for (int i = 0; i < constants.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            mv.visitFieldInsn(Opcodes.GETSTATIC, currentClassInternalName, constants.get(i), enumDesc);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitFieldInsn(Opcodes.PUTSTATIC, currentClassInternalName, "$VALUES", enumArrDesc);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(6, 0);
        mv.visitEnd();
    }

    private void generateEnumConstructor(ClassWriter cw, ClassNode cls) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>",
            "(Ljava/lang/String;I)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>",
            "(Ljava/lang/String;I)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private void generateEnumValues(ClassWriter cw, ClassNode cls) {
        String enumDesc = "L" + currentClassInternalName + ";";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "values", "()[" + enumDesc, null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, currentClassInternalName, "$VALUES", "[" + enumDesc);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "clone",
            "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[" + enumDesc);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
    }

    private void generateEnumValueOf(ClassWriter cw, ClassNode cls) {
        String enumDesc = "L" + currentClassInternalName + ";";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "valueOf", "(Ljava/lang/String;)" + enumDesc, null, null);
        mv.visitCode();
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(enumDesc));
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
            "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, currentClassInternalName);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void generateField(ClassWriter cw, FieldNode field) {
        int access = getFieldAccess(field.getVisibility());
        if (field.isStatic()) access |= Opcodes.ACC_STATIC;
        MammothType type = getFieldType(field);
        String descriptor = type.getDescriptor();
        var fv = cw.visitField(access, stripDollar(field.getName()), descriptor, null, null);
        for (AnnotationNode ann : field.getAnnotations()) {
            applyAnnotation(fv, ann);
        }
        fv.visitEnd();
    }

    private void generateAnnotationMember(ClassWriter cw, FieldNode field) {
        MammothType type = getFieldType(field);
        String descriptor = "()" + type.getDescriptor();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            stripDollar(field.getName()), descriptor, null, null);
        // If there's a default value, annotate with @AnnotationDefault
        if (field.getInitializer() != null) {
            AnnotationVisitor av = mv.visitAnnotationDefault();
            // Write default value based on type
            if (field.getInitializer() instanceof LiteralNode ln) {
                Object val = ln.getValue();
                if (val instanceof String) av.visit(null, val);
                else if (val instanceof Integer iv) av.visit(null, iv);
                else if (val instanceof Long lv) av.visit(null, lv);
                else if (val instanceof Double dv) av.visit(null, dv);
                else if (val instanceof Boolean bv) av.visit(null, bv);
            }
            av.visitEnd();
        }
        mv.visitEnd();
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
        boolean isConstructor = method.isConstructor();
        boolean isMain = method.getName().equals("main") && method.isStatic();
        boolean isStatic = method.isStatic();
        int access = getMethodAccess(method.getVisibility());

        if (isStatic) access |= Opcodes.ACC_STATIC;
        if (method.isAbstract()) access |= Opcodes.ACC_ABSTRACT;
        if (cls.isInterface() && !method.isStatic() && !method.isAbstract()) {
            // Default method in interface
        }

        MammothType returnType = getReturnType(method);
        String methodName = isConstructor ? "<init>" : method.getName();
        String descriptor;

        if (isMain) {
            descriptor = "([Ljava/lang/String;)V";
            access |= Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        } else if (isConstructor) {
            descriptor = buildConstructorDescriptor(method, cls);
        } else {
            descriptor = buildMethodDescriptor(method);
        }

        MethodVisitor mv = cw.visitMethod(access, methodName, descriptor, null, null);

        for (AnnotationNode ann : method.getAnnotations()) {
            applyAnnotation(mv, ann);
        }

        int paramIdx = 0;
        for (ParameterNode param : method.getParameters()) {
            for (AnnotationNode ann : param.getAnnotations()) {
                String annDesc = "L" + mapToAnnType(ann.getTypeName()) + ";";
                var av = mv.visitParameterAnnotation(paramIdx, annDesc, true);
                for (AnnotationNode.AnnotationArg arg : ann.getArgs()) {
                    applyArg(av, arg);
                }
                av.visitEnd();
            }
            paramIdx++;
        }

        if (method.isAbstract()) {
            mv.visitEnd();
            return;
        }

        mv.visitCode();

        SymbolTable symbolTable = analyzer.getSymbolTable();

        if (isConstructor) {
            // aload_0
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            // invokespecial parent.<init>()
            String parentName = cls.getParentClassName() != null && !cls.getParentClassName().isEmpty()
                ? cls.getParentClassName().replace('.', '/') : "java/lang/Object";
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, parentName, "<init>", "()V", false);

            // Property promotion: aload_0; aload_n; putfield for each promoted param
            int slot = 1; // skip 'this'
            for (ParameterNode param : method.getParameters()) {
                if (param.isPromoted() && param.getType() != null) {
                    MammothType pt = MammothType.fromTypeName(param.getType().getBaseTypeName());
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(pt.getLoadOpcode(), slot);
                    String fieldName = param.getName().startsWith("$") ? param.getName().substring(1) : param.getName();
                    mv.visitFieldInsn(Opcodes.PUTFIELD, currentClassInternalName, fieldName, pt.getDescriptor());
                }
                slot += (param.getType() != null
                    && (MammothType.fromTypeName(param.getType().getBaseTypeName()) == MammothType.INT64
                    || MammothType.fromTypeName(param.getType().getBaseTypeName()) == MammothType.FLOAT64)) ? 2 : 1;
            }
        }

        if (method.getBody() != null) {
            generateBlock(mv, method.getBody(), symbolTable);
        }

        if (returnType == MammothType.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }

        mv.visitMaxs(100, 100);
        mv.visitEnd();

        // Generate $default for default params
        if (!isMain && !isConstructor) {
            boolean hasDefaults = false;
            for (ParameterNode p : method.getParameters()) {
                if (p.getDefaultValue() != null) { hasDefaults = true; break; }
            }
            if (hasDefaults) {
                generateDefaultMethod(cw, method, symbolTable);
            }
        }
    }

    /**
     * Generate an interface method. Abstract if no body, default method if has body.
     */
    private void generateInterfaceMethod(ClassWriter cw, MethodNode method, ClassNode cls) {
        int access = Opcodes.ACC_PUBLIC;
        if (method.isStatic()) access |= Opcodes.ACC_STATIC;
        boolean hasBody = method.getBody() != null;
        if (!hasBody && !method.isStatic()) access |= Opcodes.ACC_ABSTRACT;

        MammothType returnType = method.getReturnType() != null
            ? MammothType.fromTypeName(method.getReturnType().getBaseTypeName())
            : MammothType.VOID;
        String descriptor = buildMethodDescriptor(method);
        MethodVisitor mv = cw.visitMethod(access, method.getName(), descriptor, null, null);

        if (!hasBody || method.isAbstract()) {
            mv.visitEnd();
            return;
        }

        mv.visitCode();
        SymbolTable symbolTable = analyzer.getSymbolTable();
        generateBlock(mv, method.getBody(), symbolTable);
        if (returnType == MammothType.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(100, 100);
        mv.visitEnd();
    }

    /**
     * Generate synthetic $default method (Kotlin-style) for default parameter values.
     * Takes all real params + an int mask. If bit i is set, param i is overwritten with its default.
     * Then delegates to the real method.
     */
    private void generateDefaultMethod(ClassWriter cw, MethodNode method, SymbolTable symbolTable) {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        String defaultName = method.getName() + "$default";
        MammothType returnType = getReturnType(method);
        List<ParameterNode> params = method.getParameters();

        // Build descriptor: same params + I (int mask), same return
        String realDesc = buildMethodDescriptor(method);
        int closeIdx = realDesc.indexOf(')');
        String paramsOnly = realDesc.substring(1, closeIdx);
        String retOnly = realDesc.substring(closeIdx + 1);
        String defaultDesc = "(" + paramsOnly + "I)" + retOnly;

        MethodVisitor mv = cw.visitMethod(access, defaultName, defaultDesc, null, null);
        mv.visitCode();

        // Compute slot for each param and the mask
        int slot = 0;
        for (ParameterNode p : params) {
            if (p.getType() != null) {
                MammothType mt = MammothType.fromTypeName(p.getType().getBaseTypeName());
                slot += (mt == MammothType.INT64 || mt == MammothType.FLOAT64) ? 2 : 1;
            } else {
                slot++;
            }
        }
        int maskSlot = slot;

        // For each param with a default: if (mask & (1<<i)) != 0 → param = default
        slot = 0;
        for (int i = 0; i < params.size(); i++) {
            ParameterNode p = params.get(i);
            MammothType pt = p.getType() != null
                ? MammothType.fromTypeName(p.getType().getBaseTypeName())
                : MammothType.STRING;

            if (p.getDefaultValue() != null) {
                mv.visitVarInsn(Opcodes.ILOAD, maskSlot);
                pushInt(mv, 1 << i);
                mv.visitInsn(Opcodes.IAND);
                Label skip = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, skip);

                // Overwrite param with default value
                generateExpression(mv, p.getDefaultValue(), symbolTable);
                MammothType defType = getExprType(p.getDefaultValue());
                emitCast(mv, defType, pt);
                mv.visitVarInsn(pt.getStoreOpcode(), slot);

                mv.visitLabel(skip);
            }
            slot += (pt == MammothType.INT64 || pt == MammothType.FLOAT64) ? 2 : 1;
        }

        // Load all params and call real method
        slot = 0;
        for (int i = 0; i < params.size(); i++) {
            ParameterNode p = params.get(i);
            if (p.getType() != null) {
                MammothType mt = MammothType.fromTypeName(p.getType().getBaseTypeName());
                mv.visitVarInsn(mt.getLoadOpcode(), slot);
                slot += (mt == MammothType.INT64 || mt == MammothType.FLOAT64) ? 2 : 1;
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                slot++;
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, currentClassInternalName, method.getName(),
            realDesc, false);

        if (returnType != MammothType.VOID) {
            mv.visitInsn(returnType.getReturnOpcode());
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(10, slot + 1);
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
        } else if (stmt instanceof TryNode tn) {
            generateTryStatement(mv, tn, symbolTable);
        } else if (stmt instanceof ThrowNode tn) {
            generateExpression(mv, tn.getExpression(), symbolTable);
            mv.visitInsn(Opcodes.ATHROW);
        } else if (stmt instanceof IfNode in) {
            generateIfStatement(mv, in, symbolTable);
        } else if (stmt instanceof WhileNode wn) {
            generateWhileStatement(mv, wn, symbolTable);
        } else if (stmt instanceof DoWhileNode dw) {
            generateDoWhileStatement(mv, dw, symbolTable);
        } else if (stmt instanceof ForNode fn) {
            generateForStatement(mv, fn, symbolTable);
        } else if (stmt instanceof ForEachNode fen) {
            generateForEachStatement(mv, fen, symbolTable);
        } else if (stmt instanceof BreakNode) {
            generateBreakContinue(mv, true);
        } else if (stmt instanceof ContinueNode) {
            generateBreakContinue(mv, false);
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

    // ======================== New expression ========================

    private void generateNewExpr(MethodVisitor mv, NewNode nn) {
        String internalName = nn.getClassName().replace('.', '/');
        // Map known types
        internalName = mapToJvmType(internalName);
        mv.visitTypeInsn(Opcodes.NEW, internalName);
        mv.visitInsn(Opcodes.DUP);

        StringBuilder ctorDesc = new StringBuilder("(");
        for (ExpressionNode arg : nn.getArguments()) {
            generateExpression(mv, arg, null);
            MammothType type = getExprType(arg);
            ctorDesc.append(type != null ? type.getDescriptor() : "Ljava/lang/Object;");
        }
        ctorDesc.append(")V");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>", ctorDesc.toString(), false);
    }

    private void generateMemberAccess(MethodVisitor mv, MemberAccessNode man, SymbolTable symbolTable) {
        generateExpression(mv, man.getTarget(), symbolTable);

        if (man.isMethodCall()) {
            // Instance method call: invokevirtual
            for (ExpressionNode arg : man.getArgs()) {
                generateExpression(mv, arg, symbolTable);
            }
            StringBuilder desc = new StringBuilder("(");
            for (ExpressionNode arg : man.getArgs()) {
                MammothType type = getExprType(arg);
                desc.append(type != null ? type.getDescriptor() : "Ljava/lang/Object;");
            }
            desc.append(")V");
            String owner = man.getFieldOwner() != null
                ? currentClassInternalName.substring(0, currentClassInternalName.lastIndexOf('/') + 1) + man.getFieldOwner().replace('.', '/')
                : currentClassInternalName;
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, man.getMemberName(), desc.toString(), false);
        } else {
            // Instance field access: getfield
            String fieldName = man.getMemberName().startsWith("$") ? man.getMemberName().substring(1) : man.getMemberName();
            String desc = man.getFieldDescriptor() != null ? man.getFieldDescriptor() : "Ljava/lang/Object;";
            String owner = man.getFieldOwner() != null
                ? currentClassInternalName.substring(0, currentClassInternalName.lastIndexOf('/') + 1) + man.getFieldOwner().replace('.', '/')
                : currentClassInternalName;
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, fieldName, desc);
        }
    }

    private void generateStaticAccess(MethodVisitor mv, StaticAccessNode san, SymbolTable symbolTable) {
        String owner = currentClassInternalName;
        if (san.getResolvedOwner() != null && !"self".equals(san.getClassName())
            && !"parent".equals(san.getClassName())) {
            owner = currentClassInternalName.substring(0, currentClassInternalName.lastIndexOf('/') + 1)
                + san.getResolvedOwner().replace('.', '/');
        } else if ("parent".equals(san.getClassName())) {
            String parentName = currentClassNode.getParentClassName();
            if (parentName != null) {
                owner = currentClassInternalName.substring(0, currentClassInternalName.lastIndexOf('/') + 1)
                    + parentName.replace('.', '/');
            }
        }

        String memberName = san.getMemberName().startsWith("$") ? san.getMemberName().substring(1) : san.getMemberName();

        if (san.isMethodCall()) {
            // Static method call
            for (ExpressionNode arg : san.getArgs()) {
                generateExpression(mv, arg, symbolTable);
            }
            StringBuilder desc = new StringBuilder("(");
            for (ExpressionNode arg : san.getArgs()) {
                MammothType type = getExprType(arg);
                desc.append(type != null ? type.getDescriptor() : "Ljava/lang/Object;");
            }
            desc.append(")V");
            if ("parent".equals(san.getClassName()) && "__construct".equals(san.getMemberName())) {
                // parent::__construct() → invokespecial super.<init>
                String parentName = currentClassNode.getParentClassName() != null
                    ? currentClassNode.getParentClassName().replace('.', '/')
                    : "java/lang/Object";
                // Args are already on stack; need aload_0 first
                // Use INVOKESPECIAL
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, parentName, "<init>", desc.toString(), false);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, memberName, desc.toString(), false);
            }
        } else {
            // Static field access: getstatic
            String desc = "Ljava/lang/Object;";
            // Try to find field type
            for (FieldNode f : currentClassNode.getFields()) {
                if (f.getName().equals(san.getMemberName()) || f.getName().equals("$" + memberName)) {
                    if (f.getType() != null) {
                        MammothType ft = MammothType.fromTypeName(f.getType().getBaseTypeName());
                        desc = ft.getDescriptor();
                    }
                    break;
                }
            }
            mv.visitFieldInsn(Opcodes.GETSTATIC, owner, memberName, desc);
        }
    }

    // ======================== Control flow generation ========================

    private final java.util.ArrayDeque<Label[]> loopLabels = new java.util.ArrayDeque<>();

    private void generateIfStatement(MethodVisitor mv, IfNode in, SymbolTable st) {
        Label elseLabel = new Label();
        Label endLabel = new Label();
        generateCondition(mv, in.getCondition(), elseLabel, false);
        generateStatement(mv, in.getThenBranch(), st);
        if (in.getElseBranch() != null) mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(elseLabel);
        if (in.getElseBranch() != null) {
            generateStatement(mv, in.getElseBranch(), st);
            mv.visitLabel(endLabel);
        }
    }

    private void generateWhileStatement(MethodVisitor mv, WhileNode wn, SymbolTable st) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        loopLabels.push(new Label[]{startLabel, endLabel});
        mv.visitLabel(startLabel);
        generateCondition(mv, wn.getCondition(), endLabel, false);
        generateStatement(mv, wn.getBody(), st);
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
        loopLabels.pop();
    }

    private void generateDoWhileStatement(MethodVisitor mv, DoWhileNode dw, SymbolTable st) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        loopLabels.push(new Label[]{startLabel, endLabel});
        mv.visitLabel(startLabel);
        generateStatement(mv, dw.getBody(), st);
        generateCondition(mv, dw.getCondition(), startLabel, true);
        mv.visitLabel(endLabel);
        loopLabels.pop();
    }

    private void generateForStatement(MethodVisitor mv, ForNode fn, SymbolTable st) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        Label updateLabel = new Label();
        loopLabels.push(new Label[]{startLabel, endLabel});
        if (fn.getInit() != null) generateStatement(mv, fn.getInit(), st);
        mv.visitLabel(startLabel);
        if (fn.getCondition() != null) {
            generateCondition(mv, fn.getCondition(), endLabel, false);
        }
        generateStatement(mv, fn.getBody(), st);
        mv.visitLabel(updateLabel);
        if (fn.getUpdate() != null) {
            generateExpression(mv, fn.getUpdate(), st);
            // Pop result of update expression
            MammothType ut = getExprType(fn.getUpdate());
            if (ut != null && ut != MammothType.VOID) {
                if (ut == MammothType.INT64 || ut == MammothType.FLOAT64) mv.visitInsn(Opcodes.POP2);
                else mv.visitInsn(Opcodes.POP);
            }
        }
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
        loopLabels.pop();
    }

    private void generateForEachStatement(MethodVisitor mv, ForEachNode fen, SymbolTable st) {
        // For now, skip foreach body generation
    }

    private void generateBreakContinue(MethodVisitor mv, boolean isBreak) {
        if (!loopLabels.isEmpty()) {
            Label target = isBreak ? loopLabels.peek()[1] : loopLabels.peek()[0];
            mv.visitJumpInsn(Opcodes.GOTO, target);
        }
    }

    /** Generate a conditional jump: if condition evaluates to 0/false, jump to label. If notIf=true, jump if true/non-zero. */
    private void generateCondition(MethodVisitor mv, ExpressionNode condition, Label label, boolean notIf) {
        generateExpression(mv, condition, null);
        MammothType type = getExprType(condition);
        // For long values, compare against 0L
        if (type == MammothType.INT64) {
            mv.visitInsn(Opcodes.LCONST_0);
            mv.visitInsn(Opcodes.LCMP);
        }
        int opcode = notIf ? Opcodes.IFNE : Opcodes.IFEQ;
        mv.visitJumpInsn(opcode, label);
    }

    private void generateTryStatement(MethodVisitor mv, TryNode tryNode, SymbolTable symbolTable) {
        Label tryStart = new Label();
        Label tryEnd = new Label();
        List<CatchClause> catches = tryNode.getCatchClauses();
        Label[] catchStarts = new Label[catches.size()];
        String[] catchTypes = new String[catches.size()];
        Label finallyStart = tryNode.hasFinally() ? new Label() : null;
        Label afterAll = new Label();

        // Map types and prepare labels
        for (int i = 0; i < catches.size(); i++) {
            catchStarts[i] = new Label();
            catchTypes[i] = mapToJvmType(catches.get(i).getExceptionType().getBaseTypeName());
            mv.visitTryCatchBlock(tryStart, tryEnd, catchStarts[i], catchTypes[i]);
        }

        // === Try body ===
        mv.visitLabel(tryStart);
        generateBlock(mv, tryNode.getTryBlock(), symbolTable);
        mv.visitLabel(tryEnd);
        if (finallyStart != null) {
            mv.visitJumpInsn(Opcodes.GOTO, finallyStart);
        } else {
            mv.visitJumpInsn(Opcodes.GOTO, afterAll);
        }

        // === Catch handlers ===
        for (int i = 0; i < catches.size(); i++) {
            mv.visitLabel(catchStarts[i]);
            CatchClause clause = catches.get(i);
            // Store the caught exception into the local variable
            if (clause.getLocalIndex() >= 0) {
                mv.visitVarInsn(Opcodes.ASTORE, clause.getLocalIndex());
                // Also need to DUP before storing if we need it for later
                // Actually, the exception is on the stack; just store it
            }
            generateBlock(mv, clause.getBody(), symbolTable);
            if (finallyStart != null) {
                mv.visitJumpInsn(Opcodes.GOTO, finallyStart);
            } else {
                mv.visitJumpInsn(Opcodes.GOTO, afterAll);
            }
        }

        // === Finally block ===
        if (finallyStart != null) {
            mv.visitLabel(finallyStart);
            // Save any pending return value on stack before finally
            // For now: simple finally without exception save/restore
            generateBlock(mv, tryNode.getFinallyBlock(), symbolTable);
        }

        mv.visitLabel(afterAll);
    }

    private String mapToJvmType(String typeName) {
        return switch (typeName) {
            case "Exception" -> "java/lang/Exception";
            case "RuntimeException" -> "java/lang/RuntimeException";
            case "ArithmeticException" -> "java/lang/ArithmeticException";
            case "Throwable" -> "java/lang/Throwable";
            case "Error" -> "java/lang/Error";
            default -> typeName.replace('.', '/');
        };
    }

    // ======================== Annotation generation ========================

    private void applyAnnotation(ClassWriter cw, AnnotationNode ann) {
        String desc = "L" + mapToAnnType(ann.getTypeName()) + ";";
        var av = cw.visitAnnotation(desc, true);
        writeAnnotationArgs(av, ann);
        av.visitEnd();
    }

    private void applyAnnotation(FieldVisitor fv, AnnotationNode ann) {
        String desc = "L" + mapToAnnType(ann.getTypeName()) + ";";
        var av = fv.visitAnnotation(desc, true);
        writeAnnotationArgs(av, ann);
        av.visitEnd();
    }

    private void applyAnnotation(MethodVisitor mv, AnnotationNode ann) {
        String desc = "L" + mapToAnnType(ann.getTypeName()) + ";";
        var av = mv.visitAnnotation(desc, true);
        writeAnnotationArgs(av, ann);
        av.visitEnd();
    }

    private void writeAnnotationArgs(AnnotationVisitor av, AnnotationNode ann) {
        for (AnnotationNode.AnnotationArg arg : ann.getArgs()) {
            applyArg(av, arg, ann.getTypeName());
        }
    }

    private void applyArg(AnnotationVisitor av, AnnotationNode.AnnotationArg arg, String annTypeName) {
        String name = arg.getName() != null ? arg.getName() : "value";
        ExpressionNode valNode = arg.getValue();
        if (valNode instanceof LiteralNode ln) {
            Object val = ln.getValue();
            if (val instanceof String) av.visit(name, val);
            else if (val instanceof Integer iv) av.visit(name, iv);
            else if (val instanceof Long lv) av.visit(name, lv);
            else if (val instanceof Double dv) av.visit(name, dv);
            else if (val instanceof Boolean bv) av.visit(name, bv);
        } else if (valNode instanceof VariableNode vn) {
            String enumValue = vn.getName();
            String enumType = deriveEnumType(annTypeName);
            av.visitEnum(name, enumType, enumValue);
        }
    }

    private String deriveEnumType(String annTypeName) {
        return switch (annTypeName) {
            case "Retention" -> "Ljava/lang/annotation/RetentionPolicy;";
            case "Target" -> "Ljava/lang/annotation/ElementType;";
            default -> "Ljava/lang/Enum;";
        };
    }

    private String mapToAnnType(String typeName) {
        return switch (typeName) {
            case "Retention" -> "java/lang/annotation/Retention";
            case "Target" -> "java/lang/annotation/Target";
            case "Deprecated" -> "java/lang/Deprecated";
            case "NotNull" -> "javax/validation/constraints/NotNull";
            default -> typeName.replace('.', '/');
        };
    }

    private void applyArg(AnnotationVisitor av, AnnotationNode.AnnotationArg arg) {
        applyArg(av, arg, "");
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
        } else if (expr instanceof NewNode nn) {
            generateNewExpr(mv, nn);
        } else if (expr instanceof MemberAccessNode man) {
            generateMemberAccess(mv, man, symbolTable);
        } else if (expr instanceof StaticAccessNode san) {
            generateStaticAccess(mv, san, symbolTable);
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
        String name = vn.getName();
        // Qualified name: e.g., Color.RED → GETSTATIC
        int dotPos = name.indexOf('.');
        if (dotPos > 0) {
            String className = name.substring(0, dotPos).replace('.', '/');
            String fieldName = name.substring(dotPos + 1);
            String owner = (currentClassInternalName.substring(0,
                currentClassInternalName.lastIndexOf('/') + 1) + className);
            mv.visitFieldInsn(Opcodes.GETSTATIC, owner, fieldName, "L" + owner + ";");
            return;
        }
        if (vn.getResolvedType() != null && vn.getLocalIndex() >= 0) {
            mv.visitVarInsn(vn.getResolvedType().getLoadOpcode(), vn.getLocalIndex());
        }
    }

    // ======================== Assignment ========================

    private void generateAssignment(MethodVisitor mv, AssignmentNode an, SymbolTable symbolTable) {
        VariableNode target = an.getTarget();

        // Static field assignment (self::$var = expr)
        if (an.isStaticAssign()) {
            generateExpression(mv, an.getValue(), symbolTable);
            String className = an.getStaticClassName();
            String owner;
            if ("self".equals(className)) {
                owner = currentClassInternalName;
            } else {
                owner = className.replace('.', '/');
            }
            String fieldName = target.getName().startsWith("$") ? target.getName().substring(1) : target.getName();
            MammothType valueType = getExprType(an.getValue());
            MammothType fieldType = MammothType.STRING;
            String staticDesc = "Ljava/lang/Object;";
            for (FieldNode f : currentClassNode.getFields()) {
                if (f.getName().equals(target.getName()) || f.getName().equals("$" + fieldName)) {
                    if (f.getType() != null) {
                        fieldType = MammothType.fromTypeName(f.getType().getBaseTypeName());
                        staticDesc = fieldType.getDescriptor();
                    }
                    break;
                }
            }
            emitCast(mv, valueType, fieldType);
            if (staticDesc.equals("J") || staticDesc.equals("D")) {
                mv.visitInsn(Opcodes.DUP2);
            } else {
                mv.visitInsn(Opcodes.DUP);
            }
            mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, fieldName, staticDesc);
            return;
        }

        // Local variable assignment
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
                mcn.setReturnType(retType);
                invokeDesc.append(retType.getDescriptor());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ifaceName, "invoke", invokeDesc.toString(), true);
            }
            return;
        }

        // Static method call
        // Look up target method to handle default parameter values
        MethodNode targetMethod = null;
        if (currentClassNode != null && !mcn.isBuiltinPrint() && !mcn.isVariableCall()) {
            for (MethodNode m : currentClassNode.getMethods()) {
                if (m.getName().equals(methodName)) {
                    targetMethod = m;
                    break;
                }
            }
        }

        List<ExpressionNode> effectiveArgs = new ArrayList<>(mcn.getArguments());
        String effectiveMethodName = methodName;
        int defaultMask = 0;
        int totalParams = 0;

        if (targetMethod != null) {
            totalParams = targetMethod.getParameters().size();
            List<String> argNames = mcn.getArgumentNames();
            boolean hasNamed = false;
            for (String an : argNames) {
                if (an != null) { hasNamed = true; break; }
            }

            // Build reordered array: [paramIndex] = argument or null
            ExpressionNode[] reordered = new ExpressionNode[totalParams];
            boolean[] filled = new boolean[totalParams];

            if (hasNamed) {
                for (int i = 0; i < mcn.getArguments().size(); i++) {
                    String argName = i < argNames.size() ? argNames.get(i) : null;
                    ExpressionNode arg = mcn.getArguments().get(i);
                    if (argName != null) {
                        String lookupName = argName.startsWith("$") ? argName : "$" + argName;
                        for (int p = 0; p < totalParams; p++) {
                            if (targetMethod.getParameters().get(p).getName().equals(lookupName)) {
                                reordered[p] = arg;
                                filled[p] = true;
                                break;
                            }
                        }
                    } else {
                        for (int p = 0; p < totalParams; p++) {
                            if (!filled[p]) { reordered[p] = arg; filled[p] = true; break; }
                        }
                    }
                }
            } else {
                // Positional only
                for (int i = 0; i < mcn.getArguments().size(); i++) {
                    if (i < totalParams) { reordered[i] = mcn.getArguments().get(i); filled[i] = true; }
                }
            }

            // Compute bitmask and build effective args (with dummy values for unfilled)
            effectiveArgs.clear();
            for (int p = 0; p < totalParams; p++) {
                if (filled[p]) {
                    effectiveArgs.add(reordered[p]);
                } else {
                    // Param not provided: check if it has a default
                    ParameterNode param = targetMethod.getParameters().get(p);
                    if (param.getDefaultValue() != null) {
                        defaultMask |= (1 << p);
                        // Push a dummy value matching the parameter type
                        effectiveArgs.add(createDummyArg(param));
                    } else {
                        // No default — stop here
                        break;
                    }
                }
            }

            // If any defaults were used, redirect to $default
            if (defaultMask != 0) {
                effectiveMethodName = methodName + "$default";
                // Append mask argument
                effectiveArgs.add(new LiteralNode(null, defaultMask, "int"));
            }
        }

        for (int i = 0; i < effectiveArgs.size(); i++) {
            ExpressionNode arg = effectiveArgs.get(i);
            generateExpression(mv, arg, symbolTable);
            // Cast argument to match parameter type (skip the mask arg)
            if (targetMethod != null && i < totalParams
                && targetMethod.getParameters().get(i).getType() != null) {
                MammothType paramType = MammothType.fromTypeName(
                    targetMethod.getParameters().get(i).getType().getBaseTypeName());
                MammothType argType = getExprType(arg);
                emitCast(mv, argType, paramType);
            }
        }
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < effectiveArgs.size(); i++) {
            ExpressionNode arg = effectiveArgs.get(i);
            MammothType type;
            if (i < totalParams && targetMethod != null
                && targetMethod.getParameters().get(i).getType() != null) {
                type = MammothType.fromTypeName(
                    targetMethod.getParameters().get(i).getType().getBaseTypeName());
            } else {
                type = getExprType(arg);
            }
            desc.append(type != null ? type.getDescriptor() : "Ljava/lang/Object;");
        }
        desc.append(")V");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, currentClassInternalName,
            effectiveMethodName, desc.toString(), false);
    }

    // ======================== Binary ops ========================

    private void generateBinaryOp(MethodVisitor mv, BinaryOpNode bon, SymbolTable symbolTable) {
        String op = bon.getOp();
        MammothType leftType = getExprType(bon.getLeft());
        MammothType rightType = getExprType(bon.getRight());

        // Logical operators (short-circuit)
        if (op.equals("&&") || op.equals("||")) {
            generateExpression(mv, bon.getLeft(), symbolTable);
            if (leftType == MammothType.INT64) { mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LCMP); }
            Label skipLabel = new Label();
            Label endLabel = new Label();
            mv.visitJumpInsn(op.equals("&&") ? Opcodes.IFEQ : Opcodes.IFNE, skipLabel);
            generateExpression(mv, bon.getRight(), symbolTable);
            if (rightType == MammothType.INT64) { mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LCMP); }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(skipLabel);
            mv.visitInsn(op.equals("&&") ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
            mv.visitLabel(endLabel);
            return;
        }

        // NOT operator
        if (op.equals("!")) {
            generateExpression(mv, bon.getLeft(), symbolTable);
            Label falseLabel = new Label();
            Label endLabel = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(falseLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitLabel(endLabel);
            return;
        }

        // Comparison operators: ==, !=, <, >, <=, >=
        if (op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            boolean isLong = leftType == MammothType.INT64 || rightType == MammothType.INT64;
            boolean isDouble = leftType == MammothType.FLOAT64 || rightType == MammothType.FLOAT64;
            generateExpression(mv, bon.getLeft(), symbolTable);
            if (isLong && leftType != MammothType.INT64) mv.visitInsn(Opcodes.I2L);
            if (isDouble && leftType != MammothType.FLOAT64) mv.visitInsn(Opcodes.I2D);
            generateExpression(mv, bon.getRight(), symbolTable);
            if (isLong && rightType != MammothType.INT64) mv.visitInsn(Opcodes.I2L);
            if (isDouble && rightType != MammothType.FLOAT64) mv.visitInsn(Opcodes.I2D);
            if (isLong) { mv.visitInsn(Opcodes.LCMP); }
            else if (isDouble) { mv.visitInsn(Opcodes.DCMPG); }
            Label trueLabel = new Label();
            Label endLabel = new Label();
            int cmpOp = switch (op) {
                case "==" -> isLong || isDouble ? Opcodes.IFEQ : Opcodes.IF_ICMPEQ;
                case "!=" -> isLong || isDouble ? Opcodes.IFNE : Opcodes.IF_ICMPNE;
                case "<" -> isLong || isDouble ? Opcodes.IFLT : Opcodes.IF_ICMPLT;
                case ">" -> isLong || isDouble ? Opcodes.IFGT : Opcodes.IF_ICMPGT;
                case "<=" -> isLong || isDouble ? Opcodes.IFLE : Opcodes.IF_ICMPLE;
                default -> isLong || isDouble ? Opcodes.IFGE : Opcodes.IF_ICMPGE;
            };
            mv.visitJumpInsn(cmpOp, trueLabel);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(trueLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitLabel(endLabel);
            return;
        }

        generateExpression(mv, bon.getLeft(), symbolTable);
        // String concatenation: check before numeric casts
        if (leftType == MammothType.STRING && op.equals("+")) {
            generateExpression(mv, bon.getRight(), symbolTable);
            if (rightType != null && rightType != MammothType.STRING && rightType != MammothType.NULL
                && rightType != MammothType.VOID) {
                String desc = rightType.getDescriptor();
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                    "(" + desc + ")Ljava/lang/String;", false);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }
        // Right side is string (e.g., $age + " years")
        if (rightType == MammothType.STRING && op.equals("+")) {
            if (leftType != null && leftType != MammothType.STRING && leftType != MammothType.NULL
                && leftType != MammothType.VOID) {
                String desc = leftType.getDescriptor();
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                    "(" + desc + ")Ljava/lang/String;", false);
            }
            generateExpression(mv, bon.getRight(), symbolTable);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            return;
        }
        // Cast left if needed for matching right type
        if (rightType == MammothType.INT64 && leftType != MammothType.INT64) mv.visitInsn(Opcodes.I2L);
        if (rightType == MammothType.FLOAT64 && leftType != MammothType.FLOAT64) mv.visitInsn(Opcodes.I2D);
        generateExpression(mv, bon.getRight(), symbolTable);
        // Cast right if needed for matching left type
        if (leftType == MammothType.INT64 && rightType != MammothType.INT64) mv.visitInsn(Opcodes.I2L);
        if (leftType == MammothType.FLOAT64 && rightType != MammothType.FLOAT64) mv.visitInsn(Opcodes.I2D);

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
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String t1, String t2) {
                if (t1.equals("java/lang/Object") || t2.equals("java/lang/Object")) return "java/lang/Object";
                try { return super.getCommonSuperClass(t1, t2); } catch (Exception e) { return "java/lang/Object"; }
            }
        };
        cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
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
        MammothType retType;
        if (closure.getReturnType() != null) {
            retType = MammothType.fromTypeName(closure.getReturnType().getBaseTypeName());
        } else if (closure.isArrowFunction()) {
            retType = inferClosureReturnType(closure);
        } else {
            retType = MammothType.VOID;
        }
        methodDesc.append(retType.getDescriptor());

        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "invoke", methodDesc.toString(), null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateClosureImplClass(ClosureNode closure, String internalName, String fnInterfaceName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String t1, String t2) {
                if (t1.equals("java/lang/Object") || t2.equals("java/lang/Object")) return "java/lang/Object";
                try { return super.getCommonSuperClass(t1, t2); } catch (Exception e) { return "java/lang/Object"; }
            }
        };
        String[] interfaces = {fnInterfaceName};
        cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
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
        MammothType retType;
        if (closure.getReturnType() != null) {
            retType = MammothType.fromTypeName(closure.getReturnType().getBaseTypeName());
        } else if (closure.isArrowFunction()) {
            retType = inferClosureReturnType(closure);
        } else {
            retType = MammothType.VOID;
        }
        methodDesc.append(retType.getDescriptor());

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke", methodDesc.toString(), null, null);
        mv.visitCode();

        if (closure.getBody() != null) {
            visitClosureBody(mv, closure, internalName);
        }

        if (retType == MammothType.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(100, 100);
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
            // Qualified name (e.g., Color.RED)
            int dotPosQ = name.indexOf('.');
            if (dotPosQ > 0) {
                String clName = name.substring(0, dotPosQ).replace('.', '/');
                String fName = name.substring(dotPosQ + 1);
                String ownerQ = (currentClassInternalName.substring(0,
                    currentClassInternalName.lastIndexOf('/') + 1) + clName);
                mv.visitFieldInsn(Opcodes.GETSTATIC, ownerQ, fName, "L" + ownerQ + ";");
                return;
            }
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
            if (vn.getName().contains(".")) return MammothType.NULL;
            TypeNode inferred = vn.getInferredType();
            if (inferred != null) return MammothType.fromTypeName(inferred.getBaseTypeName());
            return MammothType.STRING;
        }
        if (expr instanceof BinaryOpNode bon) {
            String op = bon.getOp();
            if (op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") ||
                op.equals("<=") || op.equals(">=") || op.equals("&&") || op.equals("||") || op.equals("!")) {
                return MammothType.INT32; // comparison/logical result is always boolean (int)
            }
            return getExprType(bon.getLeft()); // propagate left type
        }
        if (expr instanceof CastNode cn) {
            return MammothType.fromTypeName(cn.getTargetType().getBaseTypeName());
        }
        if (expr instanceof AssignmentNode an) {
            if (an.isStaticAssign()) {
                // For static field assignment, return the field type
                for (FieldNode f : currentClassNode.getFields()) {
                    String fn = an.getTarget().getName();
                    if (f.getName().equals(fn) || f.getName().equals(fn.startsWith("$") ? fn.substring(1) : "$" + fn)) {
                        if (f.getType() != null) return MammothType.fromTypeName(f.getType().getBaseTypeName());
                    }
                }
            }
            return getExprType(an.getValue());
        }
        if (expr instanceof MethodCallNode mcn) {
            if (mcn.isBuiltinPrint()) return MammothType.VOID;
            if (mcn.isVariableCall()) {
                if (mcn.getReturnType() != null) return mcn.getReturnType();
                return MammothType.STRING;
            }
            return MammothType.VOID;
        }
        if (expr instanceof ClosureNode) {
            return MammothType.STRING; // closure type = Object for now
        }
        if (expr instanceof NewNode) {
            return MammothType.NULL; // new returns an object reference
        }
        if (expr instanceof MemberAccessNode man) {
            if (man.isMethodCall()) return MammothType.VOID;
            return man.getFieldType() != null ? man.getFieldType() : MammothType.STRING;
        }
        if (expr instanceof StaticAccessNode san) {
            if (san.isMethodCall()) return MammothType.VOID;
            return san.getResolvedType() != null ? san.getResolvedType() : MammothType.STRING;
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

    private String buildConstructorDescriptor(MethodNode method, ClassNode cls) {
        StringBuilder sb = new StringBuilder("(");
        for (ParameterNode param : method.getParameters()) {
            if (param.getType() != null) {
                MammothType mt = MammothType.fromTypeName(param.getType().getBaseTypeName());
                sb.append(mt.getDescriptor());
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
        sb.append(")V");
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

    /**
     * Create a dummy argument AST node of the correct type for a parameter
     * whose value will be overwritten by $default based on the bitmask.
     */
    private ExpressionNode createDummyArg(ParameterNode param) {
        if (param.getType() != null) {
            MammothType mt = MammothType.fromTypeName(param.getType().getBaseTypeName());
            return switch (mt) {
                case INT8, INT16, INT32, INT64, BOOLEAN -> new LiteralNode(null, 0, "int");
                case FLOAT32, FLOAT64 -> new LiteralNode(null, 0.0, "float");
                default -> new LiteralNode(null, null, "null");
            };
        }
        return new LiteralNode(null, null, "null");
    }

    private MammothType inferClosureReturnType(ClosureNode closure) {
        if (closure.getBody() != null) {
            for (StatementNode stmt : closure.getBody().getStatements()) {
                if (stmt instanceof ReturnNode rn && rn.hasValue()) {
                    ExpressionNode val = rn.getValue();
                    if (val instanceof LiteralNode ln) {
                        return MammothType.inferFromLiteral(ln.getTypeHint());
                    }
                    if (val instanceof VariableNode vn) {
                        // Check if it's a captured variable
                        for (CaptureItem cap : closure.getCaptures()) {
                            if (cap.getVariableName().equals(vn.getName()) && cap.getResolvedType() instanceof MammothType mt) {
                                return mt;
                            }
                        }
                    }
                }
            }
        }
        return MammothType.STRING; // default to Object reference
    }
}

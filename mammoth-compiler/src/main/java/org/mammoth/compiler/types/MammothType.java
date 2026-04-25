package org.mammoth.compiler.types;

import org.objectweb.asm.Opcodes;

public enum MammothType {
    STRING("string", "Ljava/lang/String;", String.class),
    BOOLEAN("boolean", "Z", boolean.class),
    INT8("int8", "B", byte.class),
    INT16("int16", "S", short.class),
    INT32("int32", "I", int.class),
    INT64("int64", "J", long.class),
    FLOAT32("float32", "F", float.class),
    FLOAT64("float64", "D", double.class),
    VOID("void", "V", void.class),
    NULL("null", "Ljava/lang/Object;", null);

    private final String mammothName;
    private final String descriptor;
    private final Class<?> javaClass;

    MammothType(String mammothName, String descriptor, Class<?> javaClass) {
        this.mammothName = mammothName;
        this.descriptor = descriptor;
        this.javaClass = javaClass;
    }

    public String getMammothName() { return mammothName; }
    public String getDescriptor() { return descriptor; }
    public Class<?> getJavaClass() { return javaClass; }

    public boolean isIntegral() {
        return this == INT8 || this == INT16 || this == INT32 || this == INT64;
    }

    public boolean isFloatingPoint() {
        return this == FLOAT32 || this == FLOAT64;
    }

    public boolean isNumeric() {
        return isIntegral() || isFloatingPoint();
    }

    /**
     * Resolve a type name to MammothType.
     * Aliases: byte -> INT8, int -> INT64, float -> FLOAT64
     */
    public static MammothType fromTypeName(String name) {
        return switch (name) {
            case "string"  -> STRING;
            case "boolean" -> BOOLEAN;
            case "int8"    -> INT8;
            case "int16"   -> INT16;
            case "int32"   -> INT32;
            case "int64"   -> INT64;
            case "float32" -> FLOAT32;
            case "float64" -> FLOAT64;
            case "byte"    -> INT8;
            case "int"     -> INT64;
            case "float"   -> FLOAT64;
            case "void"    -> VOID;
            case "null"    -> NULL;
            default -> throw new IllegalArgumentException("Unknown type: " + name);
        };
    }

    public int getReturnOpcode() {
        return switch (this) {
            case INT8, INT16, INT32, BOOLEAN -> Opcodes.IRETURN;
            case INT64 -> Opcodes.LRETURN;
            case FLOAT32 -> Opcodes.FRETURN;
            case FLOAT64 -> Opcodes.DRETURN;
            case STRING, NULL -> Opcodes.ARETURN;
            case VOID -> Opcodes.RETURN;
        };
    }

    public int getLoadOpcode() {
        return switch (this) {
            case INT8, INT16, INT32, BOOLEAN -> Opcodes.ILOAD;
            case INT64 -> Opcodes.LLOAD;
            case FLOAT32 -> Opcodes.FLOAD;
            case FLOAT64 -> Opcodes.DLOAD;
            case STRING, NULL -> Opcodes.ALOAD;
            default -> throw new IllegalStateException("No load opcode for " + this);
        };
    }

    public int getStoreOpcode() {
        return switch (this) {
            case INT8, INT16, INT32, BOOLEAN -> Opcodes.ISTORE;
            case INT64 -> Opcodes.LSTORE;
            case FLOAT32 -> Opcodes.FSTORE;
            case FLOAT64 -> Opcodes.DSTORE;
            case STRING, NULL -> Opcodes.ASTORE;
            default -> throw new IllegalStateException("No store opcode for " + this);
        };
    }

    public String getInternalName() {
        return descriptor.substring(1, descriptor.length() - 1);
    }

    public String getCastOpcode(MammothType from) {
        if (this == from) return null;
        if (!this.isNumeric() || !from.isNumeric()) return null;

        int fromSize = getWidth(from);
        int toSize = getWidth(this);

        if (fromSize < toSize) {
            if (from == FLOAT32 && this == FLOAT64) return "F2D";
            if (from.isIntegral() && this == INT64) return "I2L";
            if (from.isIntegral() && this == FLOAT32) return "I2F";
            if (from.isIntegral() && this == FLOAT64) return "I2D";
            if (from == INT64 && this == FLOAT32) return "L2F";
            if (from == INT64 && this == FLOAT64) return "L2D";
        } else if (fromSize > toSize) {
            if (from == INT64 && this.isIntegral()) return "L2I";
            if (from == FLOAT64 && this == FLOAT32) return "D2F";
            if (from == FLOAT64 && this.isIntegral()) return "D2I";
            if (from == FLOAT32 && this.isIntegral()) return "F2I";
        }
        return null;
    }

    private static int getWidth(MammothType t) {
        return switch (t) {
            case INT8, INT16 -> 1;
            case INT32, BOOLEAN, FLOAT32 -> 2;
            case INT64 -> 3;
            case FLOAT64 -> 4;
            default -> -1;
        };
    }

    public static MammothType inferFromLiteral(String typeHint) {
        return switch (typeHint) {
            case "string" -> STRING;
            case "int" -> INT32;
            case "float" -> FLOAT64;
            case "boolean" -> BOOLEAN;
            case "null" -> NULL;
            default -> throw new IllegalArgumentException("Unknown literal type: " + typeHint);
        };
    }
}

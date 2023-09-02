package raylras.zen.code.type;

import raylras.zen.code.CompilationEnvironment;

public abstract class Type {

    public boolean isAssignableTo(Type type, CompilationEnvironment env) {
        return isSubtypeOf(type, env).matched();
    }

    public SubtypeResult isSubtypeOf(Type type, CompilationEnvironment env) {
        if (this.equals(type)) {
            return SubtypeResult.SELF;
        }
        if (type.equals(AnyType.INSTANCE)) {
            return SubtypeResult.INHERIT;
        }
        return SubtypeResult.MISMATCH;
    }

    public abstract String toString();

}

package raylras.zen.model.type;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FunctionType extends Type {

    private final Type returnType;
    private final List<Type> parameterTypes;

    public FunctionType(Type returnType, List<Type> parameterTypes) {
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public FunctionType(Type returnType, Type... parameterTypes) {
        this.returnType = returnType;
        this.parameterTypes = Arrays.asList(parameterTypes);
    }

    public Type getReturnType() {
        return returnType;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public String toString() {
        return "function" + parameterTypes.stream().map(Objects::toString).collect(Collectors.joining(",", "(", ")")) + returnType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionType that = (FunctionType) o;
        return Objects.equals(returnType, that.returnType) && Objects.equals(parameterTypes, that.parameterTypes);
    }
}

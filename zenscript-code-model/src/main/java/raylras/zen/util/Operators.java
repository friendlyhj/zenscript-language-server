package raylras.zen.util;

import raylras.zen.model.CompilationEnvironment;
import raylras.zen.model.symbol.Operator;
import raylras.zen.model.symbol.OperatorFunctionSymbol;
import raylras.zen.model.symbol.ParameterSymbol;
import raylras.zen.model.symbol.Symbol;
import raylras.zen.model.type.AnyType;
import raylras.zen.model.type.SubtypeResult;
import raylras.zen.model.type.Type;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Operators {
    public static List<OperatorFunctionSymbol> find(Type type, CompilationEnvironment env, Operator operator) {
        return Symbols.getMember(type, OperatorFunctionSymbol.class, env, it -> it.getOperator() == operator);
    }

    public static List<OperatorFunctionSymbol> find(Type type, CompilationEnvironment env, Operator... operators) {
        Set<Operator> candidates = Set.of(operators);
        return Symbols.getMember(type, OperatorFunctionSymbol.class, env, it -> candidates.contains(it.getOperator()));
    }

    public static Type getBinaryOperatorResult(Type type, Operator operator, CompilationEnvironment env, Type rightType) {
        return find(type, env, operator).stream()
                .min(Comparator.comparing(it -> rightType.testSubtypeOf(it.getParameterList().get(0).getType(), env), SubtypeResult.PRIORITY_COMPARATOR))
                .map(OperatorFunctionSymbol::getReturnType)
                .orElse(AnyType.INSTANCE);
    }

    public static Type getUnaryOperatorResult(Type type, Operator operator, CompilationEnvironment env) {
        return find(type, env, operator).stream()
                .findFirst()
                .map(OperatorFunctionSymbol::getReturnType)
                .orElse(AnyType.INSTANCE);
    }

    public static Optional<OperatorFunctionSymbol> findBestBinaryOperator(List<Symbol> symbols, Type rightType, CompilationEnvironment env) {
        return symbols.stream()
                .filter(it -> it instanceof OperatorFunctionSymbol)
                .map(it -> (OperatorFunctionSymbol) it)
                .min(Comparator.comparing(it -> rightType.testSubtypeOf(it.getParameterList().get(0).getType(), env), SubtypeResult.PRIORITY_COMPARATOR));
    }

    public static Type getTrinaryOperatorResult(Type type, Operator operator, CompilationEnvironment env, Type rightType1, Type rightType2) {
        return find(type, env, operator).stream()
                .min(Comparator.comparing(it -> {
                    List<ParameterSymbol> parameterList = it.getParameterList();
                    return SubtypeResult.higher(rightType1.testSubtypeOf(parameterList.get(0).getType(), env), rightType2.testSubtypeOf(parameterList.get(1).getType(), env));
                }, SubtypeResult.PRIORITY_COMPARATOR))
                .map(OperatorFunctionSymbol::getReturnType)
                .orElse(AnyType.INSTANCE);
    }

    public static boolean hasCaster(Type from, Type to, CompilationEnvironment env) {
        Type result = getUnaryOperatorResult(from, Operator.AS, env);
        return result.isInheritedFrom(to);
    }

    public static Operator of(String literal, int params) {
        Operator.OperatorType operatorType = switch (params) {
            case 0 -> Operator.OperatorType.UNARY;
            case 1 -> Operator.OperatorType.BINARY;
            case 2 -> Operator.OperatorType.TRINARY;
            default -> throw new IllegalArgumentException("No such operator for " + params + " parameters");
        };
        return operatorType.getOperators().getOrDefault(literal, Operator.ERROR);
    }

    public static Operator of(String literal, Operator.OperatorType type) {
        return type.getOperators().getOrDefault(literal, Operator.ERROR);
    }

}

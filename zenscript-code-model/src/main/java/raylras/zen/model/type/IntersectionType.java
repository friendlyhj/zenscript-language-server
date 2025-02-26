package raylras.zen.model.type;

import raylras.zen.model.CompilationEnvironment;
import raylras.zen.model.SymbolProvider;
import raylras.zen.model.symbol.Symbol;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class IntersectionType extends Type implements SymbolProvider {

    private final List<Type> typeList;

    public IntersectionType(List<Type> typeList) {
        this.typeList = typeList;
    }

    @Override
    public Collection<Symbol> getSymbols() {
        MemberValidator validator = new MemberValidator();
        for (Type type : typeList) {
            if (type instanceof SymbolProvider symbolProvider) {
                validator.addAll(symbolProvider);
            }
        }
        return validator.getMembers();
    }

    @Override
    public boolean isInheritedFrom(Type type) {
        return typeList.stream().anyMatch(it -> it.isInheritedFrom(type));
    }

    @Override
    public boolean isCastableTo(Type type, CompilationEnvironment env) {
        return typeList.stream().anyMatch(it -> it.isCastableTo(type, env));
    }

    public List<Type> getTypeList() {
        return typeList;
    }

    @Override
    public String toString() {
        return typeList.stream().map(Object::toString).collect(Collectors.joining(" & "));
    }

}

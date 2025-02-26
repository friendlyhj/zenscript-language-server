package raylras.zen.model;

import raylras.zen.model.symbol.Symbol;
import raylras.zen.model.type.Type;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface SymbolProvider extends Iterable<Symbol> {

    Collection<Symbol> getSymbols();

    SymbolProvider EMPTY = Collections::emptyList;

    static SymbolProvider of(Collection<Symbol> members) {
        return () -> members;
    }

    default Symbol getFirst() {
        return getSymbols().iterator().next();
    }

    default SymbolProvider filter(Predicate<Symbol> predicate) {
        return () -> getSymbols().stream().filter(predicate).toList();
    }

    default SymbolProvider limit(long maxSize) {
        return () -> getSymbols().stream().limit(maxSize).toList();
    }

    default SymbolProvider merge(SymbolProvider other) {
        return () -> Stream.concat(getSymbols().stream(), other.getSymbols().stream()).toList();
    }

    default SymbolProvider orElse(SymbolProvider other) {
        Collection<Symbol> symbols = getSymbols();
        if (symbols.isEmpty()) {
            return other;
        }
        return () -> symbols;
    }

    default int size() {
        return getSymbols().size();
    }

    default Stream<Symbol> stream() {
        return getSymbols().stream();
    }

    default SymbolProvider withExpands(CompilationEnvironment env) {
        if (this instanceof Type type) {
            return merge(() -> env.getExpandMembers(type));
        } else {
            return this;
        }
    }

    @Override
    default Iterator<Symbol> iterator() {
        return getSymbols().iterator();
    }

}

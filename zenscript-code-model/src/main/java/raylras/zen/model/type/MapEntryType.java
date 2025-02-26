package raylras.zen.model.type;

import raylras.zen.model.SymbolProvider;
import raylras.zen.model.symbol.Symbol;
import raylras.zen.model.symbol.SymbolFactory;

import java.util.List;
import java.util.Objects;

public class MapEntryType extends Type implements SymbolProvider {

    private final Type keyType;
    private final Type valueType;

    public MapEntryType(Type keyType, Type valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public Type getKeyType() {
        return keyType;
    }

    public Type getValueType() {
        return valueType;
    }

    @Override
    public List<Symbol> getSymbols() {
        return SymbolFactory.builtinSymbols()
                .variable("key", keyType, Symbol.Modifier.VAL)
                .variable("value", valueType, Symbol.Modifier.VAL)
                .build();
    }

    @Override
    public String toString() {
        return "Map.Entry<" + keyType + "," + valueType + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapEntryType symbols = (MapEntryType) o;
        return Objects.equals(keyType, symbols.keyType) && Objects.equals(valueType, symbols.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }
}

package raylras.zen.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raylras.zen.bracket.BracketHandlerService;
import raylras.zen.model.symbol.ClassSymbol;
import raylras.zen.model.symbol.ExpandFunctionSymbol;
import raylras.zen.model.symbol.Symbol;
import raylras.zen.model.type.ClassType;
import raylras.zen.model.type.Type;
import raylras.zen.util.PathUtils;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompilationEnvironment {

    private static final Logger logger = LoggerFactory.getLogger(CompilationEnvironment.class);

    public static final String DEFAULT_ROOT_DIRECTORY = "scripts";
    public static final String DEFAULT_GENERATED_DIRECTORY = "generated";

    private final Path root;
    private final Path generatedRoot;
    private final Map<Path, CompilationUnit> unitMap = new HashMap<>();
    private final BracketHandlerService bracketHandlerService = new BracketHandlerService(this);

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public CompilationEnvironment(Path root) {
        Objects.requireNonNull(root);
        this.root = root;
        this.generatedRoot = resolveGeneratedRoot(this);
    }

    public CompilationUnit createUnit(Path unitPath) {
        CompilationUnit unit = new CompilationUnit(unitPath, this);
        unitMap.put(unitPath, unit);
        return unit;
    }

    public CompilationUnit getUnit(Path unitPath) {
        return unitMap.get(unitPath);
    }

    public void removeUnit(Path unitPath) {
        unitMap.remove(unitPath);
    }

    public Collection<CompilationUnit> getUnits() {
        return unitMap.values();
    }

    public Map<Path, CompilationUnit> getUnitMap() {
        return unitMap;
    }

    public List<Symbol> getGlobalSymbols() {
        return getUnits().stream()
                .flatMap(unit -> unit.getTopLevelSymbols().stream())
                .filter(symbol -> symbol.isModifiedBy(Symbol.Modifier.GLOBAL))
                .collect(Collectors.toList());
    }

    public List<ExpandFunctionSymbol> getExpandFunctions() {
        return getUnits().stream()
                .flatMap(unit -> unit.getTopLevelSymbols().stream())
                .filter(ExpandFunctionSymbol.class::isInstance)
                .map(ExpandFunctionSymbol.class::cast)
                .toList();
    }

    public Map<String, ClassType> getClassTypeMap() {
        return getUnits().stream()
                .flatMap(unit -> unit.getTopLevelSymbols().stream())
                .filter(ClassSymbol.class::isInstance)
                .map(ClassSymbol.class::cast)
                .collect(Collectors.toMap(ClassSymbol::getQualifiedName, ClassSymbol::getType));
    }

    public Map<String, ClassSymbol> getClassSymbolMap() {
        return getUnits().stream()
                .flatMap(unit -> unit.getTopLevelSymbols().stream())
                .filter(ClassSymbol.class::isInstance)
                .map(ClassSymbol.class::cast)
                .collect(Collectors.toMap(ClassSymbol::getQualifiedName, Function.identity()));
    }

    public Path getRoot() {
        return root;
    }

    public Path getGeneratedRoot() {
        return generatedRoot;
    }

    public BracketHandlerService getBracketHandlerService() {
        return bracketHandlerService;
    }

    public List<Symbol> getExpandMembers(Type type) {
        List<Symbol> expands = getExpandFunctions().stream()
                .filter(symbol -> type.isInheritedFrom(symbol.getExpandingType()))
                .map(Symbol.class::cast)
                .toList();
        if (type instanceof ClassType) {
            return expands;
        } else {
            List<Symbol> symbols = new ArrayList<>(expands);
            symbols.addAll(getPrimitiveTypeExpandMembers(type));
            return symbols;
        }
    }

    public ReentrantReadWriteLock.ReadLock readLock() {
        return readWriteLock.readLock();
    }

    public ReentrantReadWriteLock.WriteLock writeLock() {
        return readWriteLock.writeLock();
    }

    @Override
    public String toString() {
        return root.toString();
    }

    private Collection<Symbol> getPrimitiveTypeExpandMembers(Type type) {
        String typeName = type.toString();
        Map<String, ClassType> classTypeMap = getClassTypeMap();
        ClassType dumpClassType = classTypeMap.get(typeName);
        return dumpClassType != null ? dumpClassType.getSymbols() : Collections.emptyList();
    }

    private static Path resolveGeneratedRoot(CompilationEnvironment env) {
        return FileSystems.getDefault()
                .getPath(System.getProperty("user.home"))
                .resolve(".probezs")
                .resolve(PathUtils.toHash(env.getRoot()))
                .resolve(DEFAULT_GENERATED_DIRECTORY);
    }

}

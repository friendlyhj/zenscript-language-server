package raylras.zen.model.resolve;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import raylras.zen.model.CompilationUnit;
import raylras.zen.model.SymbolProvider;
import raylras.zen.model.Visitor;
import raylras.zen.model.parser.ZenScriptLexer;
import raylras.zen.model.parser.ZenScriptParser.*;
import raylras.zen.model.scope.Scope;
import raylras.zen.model.symbol.*;
import raylras.zen.model.symbol.Operator;
import raylras.zen.model.symbol.Operator.OperatorType;
import raylras.zen.model.type.*;
import raylras.zen.util.CSTNodes;
import raylras.zen.util.Executables;
import raylras.zen.util.Operators;
import raylras.zen.util.Symbols;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TypeResolver {

    private TypeResolver() {
    }

    public static Type getType(ParseTree cst, CompilationUnit unit) {
        Objects.requireNonNull(cst);
        Objects.requireNonNull(unit);
        Type type = cst.accept(new TypeVisitor(unit));
        return type == null ? AnyType.INSTANCE : type;
    }

    private static final class TypeVisitor extends Visitor<Type> {
        private final CompilationUnit unit;

        public TypeVisitor(CompilationUnit unit) {
            this.unit = unit;
        }

        private List<Type> toTypeList(FormalParameterListContext ctx) {
            return ctx.formalParameter().stream()
                    .map(this::visit)
                    .collect(Collectors.toList());
        }

        private List<Type> toTypeList(TypeLiteralListContext ctx) {
            return ctx.typeLiteral().stream()
                    .map(this::visit)
                    .collect(Collectors.toList());
        }

        private Symbol lookupSymbol(ParseTree cst, String simpleName) {
            Scope scope = unit.lookupScope(cst);
            Symbol symbol = null;
            if (scope != null) {
                symbol = scope.lookupSymbol(simpleName);
            }
            if (symbol == null) {
                for (Symbol globalSymbol : unit.getEnv().getGlobalSymbols()) {
                    if (simpleName.equals(globalSymbol.getName())) {
                        symbol = globalSymbol;
                    }
                }
            }
            return symbol;
        }

        @Override
        public Type visitImportDeclaration(ImportDeclarationContext ctx) {
            ImportSymbol symbol = unit.getSymbol(ctx, ImportSymbol.class);
            if (symbol != null) {
                return symbol.getType();
            } else {
                return AnyType.INSTANCE;
            }
        }

        @Override
        public Type visitFunctionDeclaration(FunctionDeclarationContext ctx) {
            List<Type> paramTypes = toTypeList(ctx.formalParameterList());
            Type returnType = visit(ctx.returnType());
            if (returnType == null) {
                returnType = AnyType.INSTANCE;
            }
            return new FunctionType(returnType, paramTypes);
        }

        @Override
        public Type visitExpandFunctionDeclaration(ExpandFunctionDeclarationContext ctx) {
            List<Type> paramTypes = toTypeList(ctx.formalParameterList());
            Type returnType = visit(ctx.returnType());
            if (returnType == null) {
                returnType = AnyType.INSTANCE;
            }
            return new FunctionType(returnType, paramTypes);
        }

        @Override
        public Type visitFormalParameter(FormalParameterContext ctx) {
            if (ctx.typeLiteral() != null) {
                return visit(ctx.typeLiteral());
            }
            if (ctx.defaultValue() != null) {
                return visit(ctx.defaultValue());
            }
            FormalParameterListContext parameterList = (FormalParameterListContext) ctx.getParent();
            FunctionExprContext functionExpr = ((FunctionExprContext) parameterList.getParent());
            Type functionType = visit(functionExpr);
            int argumentIndex = parameterList.formalParameter().indexOf(ctx);
            if (functionType instanceof FunctionType) {
                return ((FunctionType) functionType).getParameterTypes().get(argumentIndex);
            } else if (functionType instanceof ClassType classType) {
                return Executables.findLambdaForm(classType, unit.getEnv())
                        .map(it -> it.getParameterTypes().get(argumentIndex))
                        .orElse(AnyType.INSTANCE);
            }
            return AnyType.INSTANCE;
        }

        @Override
        public Type visitDefaultValue(DefaultValueContext ctx) {
            return visit(ctx.expression());
        }

        @Override
        public Type visitReturnType(ReturnTypeContext ctx) {
            return visit(ctx.intersectionType() != null ? ctx.intersectionType() : ctx.typeLiteral());
        }

        @Override
        public Type visitClassDeclaration(ClassDeclarationContext ctx) {
            ClassSymbol symbol = unit.getSymbol(ctx, ClassSymbol.class);
            if (symbol != null) {
                return symbol.getType();
            } else {
                return AnyType.INSTANCE;
            }
        }

        @Override
        public Type visitConstructorDeclaration(ConstructorDeclarationContext ctx) {
            List<Type> paramTypes = toTypeList(ctx.formalParameterList());
            Type returnType;
            if (unit.getSymbol(ctx) instanceof ConstructorSymbol symbol) {
                returnType = symbol.getDeclaringClass().getType();
            } else {
                returnType = AnyType.INSTANCE;
            }
            return new FunctionType(returnType, paramTypes);
        }

        @Override
        public Type visitVariableDeclaration(VariableDeclarationContext ctx) {
            if (ctx.intersectionType() != null) {
                return visit(ctx.intersectionType());
            }
            if (ctx.typeLiteral() != null) {
                return visit(ctx.typeLiteral());
            } else {
                return visit(ctx.initializer());
            }
        }

        @Override
        public Type visitInitializer(InitializerContext ctx) {
            return visit(ctx.expression());
        }

        @Override
        public Type visitOperatorFunctionDeclaration(OperatorFunctionDeclarationContext ctx) {
            List<Type> paramTypes = toTypeList(ctx.formalParameterList());
            Type returnType = visit(ctx.intersectionType());
            return new FunctionType(returnType, paramTypes);
        }

        @Override
        public Type visitIntersectionType(IntersectionTypeContext ctx) {
            List<TypeLiteralContext> types = ctx.typeLiteral();
            if (types.size() == 1) {
                return visit(types.get(0));
            } else {
                return new IntersectionType(types.stream().map(this::visit).collect(Collectors.toList()));
            }
        }

        @Override
        public Type visitForeachVariable(ForeachVariableContext ctx) {
            // variable -> variableList -> forEach
            ForeachStatementContext forEachStatement = (ForeachStatementContext) ctx.getParent().getParent();
            List<ForeachVariableContext> variables = forEachStatement.foreachVariableList().foreachVariable();
            Type iterableType = visit(forEachStatement.expression());
            Type result = Operators.getUnaryOperatorResult(iterableType, Operator.ITERATOR, unit.getEnv());
            if (result instanceof ListType listType) {
                return getListForeachVariableType(listType.getElementType(), ctx, variables);
            }
            if (result instanceof MapType mapType) {
                return getMapForeachVariableType(mapType, ctx, variables);
            }
            return AnyType.INSTANCE;
        }

        @Override
        public Type visitAssignmentExpr(AssignmentExprContext ctx) {
            return visit(ctx.left);
        }

        @Override
        public Type visitThisExpr(ThisExprContext ctx) {
            Symbol symbol = lookupSymbol(ctx, "this");
            if (symbol != null) {
                return symbol.getType();
            } else {
                return AnyType.INSTANCE;
            }
        }

        @Override
        public Type visitMapLiteralExpr(MapLiteralExprContext ctx) {
            if (ctx.mapEntryList() == null) {
                return new MapType(AnyType.INSTANCE, AnyType.INSTANCE);
            }
            MapEntryContext firstEntry = ctx.mapEntryList().mapEntry(0);
            Type keyType = visit(firstEntry.key);
            Type valueType = visit(firstEntry.value);
            return new MapType(keyType, valueType);
        }

        @Override
        public Type visitIntRangeExpr(IntRangeExprContext ctx) {
            return Operators.getBinaryOperatorResult(visit(ctx.from), Operator.RANGE, unit.getEnv(), visit(ctx.to));
        }

        @Override
        public Type visitSimpleNameExpr(SimpleNameExprContext ctx) {
            Symbol symbol = lookupSymbol(ctx, ctx.simpleName().getText());
            if (symbol != null) {
                return symbol.getType();
            } else {
                return AnyType.INSTANCE;
            }
        }

        @Override
        public Type visitBinaryExpr(BinaryExprContext ctx) {
            Type leftType = visit(ctx.left);
            Operator operator = Operators.of(ctx.op.getText(), OperatorType.BINARY);
            if (operator != Operator.ERROR) {
                return Operators.getBinaryOperatorResult(leftType, operator, unit.getEnv(), visit(ctx.right));
            } else {
                return leftType;
            }
        }

        @Override
        public Type visitCompareExpr(CompareExprContext ctx) {
            Type leftType = visit(ctx.left);
            Type result = Operators.getBinaryOperatorResult(leftType, Operator.COMPARE, unit.getEnv(), visit(ctx.right));
            if (IntType.INSTANCE.equals(result)) {
                return BoolType.INSTANCE;
            }
            if (ctx.EQUAL() != null || ctx.NOT_EQUAL() != null) {
                return Operators.getBinaryOperatorResult(leftType, Operator.EQUALS, unit.getEnv(), visit(ctx.right));
            }
            return AnyType.INSTANCE;
        }

        @Override
        public Type visitLogicExpr(LogicExprContext ctx) {
            return visit(ctx.left);
        }

        @Override
        public Type visitParensExpr(ParensExprContext ctx) {
            return visit(ctx.expression());
        }

        @Override
        public Type visitTypeCastExpr(TypeCastExprContext ctx) {
            return visit(ctx.typeLiteral());
        }

        @Override
        public Type visitFunctionExpr(FunctionExprContext ctx) {
            if (ctx.typeLiteral() != null) {
                return visit(ctx.typeLiteral());
            } else {
                // functionExpr -> assignExpr|callExpr
                ParserRuleContext caller = ctx.getParent();
                if (caller instanceof AssignmentExprContext) {
                    Type leftType = visit(caller);
                    if (leftType != null) {
                        return leftType;
                    }
                } else if (caller.getParent() instanceof CallExprContext callExpr) {
                    ExpressionContext expression = callExpr.expression();
                    if (expression instanceof MemberAccessExprContext memberAccessExpr) {
                        List<Type> argumentTypes = new ArrayList<>();
                        List<ExpressionContext> callExpressions = callExpr.expressionList().expression();
                        int functionExprPosition = callExpressions.indexOf(ctx);
                        for (int i = 0; i < functionExprPosition; i++) {
                            Type argumentType = visit(callExpressions.get(i));
                            if (argumentType == null) {
                                argumentType = AnyType.INSTANCE;
                            }
                            argumentTypes.add(argumentType);
                        }
                        Type type = visit(memberAccessExpr.expression());
                        String name = memberAccessExpr.simpleName().getText();
                        List<Executable> functions = Symbols.getExecutableMembersByName(type, name, unit.getEnv());
                        return Executables.predictNextArgumentType(functions, argumentTypes, unit.getEnv());
                    }
                }
            }
            List<Type> paramTypes = new ArrayList<>();
            for (int i = 0; i < ctx.formalParameterList().formalParameter().size(); i++) {
                paramTypes.add(AnyType.INSTANCE);
            }
            return new FunctionType(AnyType.INSTANCE, paramTypes);
        }

        @Override
        public Type visitBracketHandlerExpr(BracketHandlerExprContext ctx) {
            // FIXME
            return AnyType.INSTANCE;
        }

        @Override
        public Type visitUnaryExpr(UnaryExprContext ctx) {
            Type type = visit(ctx.expression());
            if (ctx.NOT() != null) {
                return Operators.getUnaryOperatorResult(type, Operator.NOT, unit.getEnv());
            }
            if (ctx.SUB() != null) {
                return Operators.getUnaryOperatorResult(type, Operator.NEG, unit.getEnv());
            }
            return type;
        }

        @Override
        public Type visitTernaryExpr(TernaryExprContext ctx) {
            return visit(ctx.truePart);
        }

        @Override
        public Type visitLiteralExpr(LiteralExprContext ctx) {
            return switch (CSTNodes.getTokenType(ctx.start)) {
                case ZenScriptLexer.INT_LITERAL -> IntType.INSTANCE;
                case ZenScriptLexer.LONG_LITERAL -> LongType.INSTANCE;
                case ZenScriptLexer.FLOAT_LITERAL -> FloatType.INSTANCE;
                case ZenScriptLexer.DOUBLE_LITERAL -> DoubleType.INSTANCE;
                case ZenScriptLexer.STRING_LITERAL -> StringType.INSTANCE;
                case ZenScriptLexer.TRUE_LITERAL, ZenScriptLexer.FALSE_LITERAL -> BoolType.INSTANCE;
                case ZenScriptLexer.NULL_LITERAL -> AnyType.INSTANCE;
                default -> null;
            };
        }

        @Override
        public Type visitMemberAccessExpr(MemberAccessExprContext ctx) {
            Type leftType = visit(ctx.expression());
            if (!(leftType instanceof SymbolProvider symbolProvider)) {
                return null;
            }
            if (ctx.simpleName() != null) {
                String simpleName = ctx.simpleName().getText();
                for (Symbol member : symbolProvider.withExpands(unit.getEnv())) {
                    if (Objects.equals(member.getName(), simpleName)) {
                        return member.getType();
                    }
                }
            }
            return Operators.getBinaryOperatorResult(leftType, Operator.MEMBER_GET, unit.getEnv(), StringType.INSTANCE);
        }

        @Override
        public Type visitArrayLiteralExpr(ArrayLiteralExprContext ctx) {
            Type firstElementType = visit(ctx.expressionList().expression(0));
            return new ArrayType(Objects.requireNonNullElse(firstElementType, AnyType.INSTANCE));
        }

        @Override
        public Type visitCallExpr(CallExprContext ctx) {
            if (ctx.expression() instanceof MemberAccessExprContext memberAccessExpr) {
                Type owner = visit(memberAccessExpr.expression());
                if (owner == null) {
                    return null;
                }
                List<Type> argumentTypes = new ArrayList<>();
                for (ExpressionContext expressionContext : ctx.expressionList().expression()) {
                    Type argumentType = visit(expressionContext);
                    if (argumentType == null) {
                        argumentType = AnyType.INSTANCE;
                    }
                    argumentTypes.add(argumentType);
                }
                List<Executable> functions = Symbols.getExecutableMembersByName(owner, memberAccessExpr.simpleName().getText(), unit.getEnv());
                Executable matchedFunction = Executables.findBestMatch(functions, argumentTypes, unit.getEnv());
                return matchedFunction == null ? null : matchedFunction.getReturnType();
            } else {
                Type leftType = visit(ctx.expression());
                if (leftType instanceof FunctionType) {
                    return ((FunctionType) leftType).getReturnType();
                } else if (leftType instanceof ClassType) {
                    return leftType;
                } else {
                    return null;
                }
            }
        }

        @Override
        public Type visitMemberIndexExpr(MemberIndexExprContext ctx) {
            Type leftType = visit(ctx.left);
            return Operators.getBinaryOperatorResult(leftType, Operator.INDEX_GET, unit.getEnv(), visit(ctx.index));
        }

        @Override
        public Type visitArrayType(ArrayTypeContext ctx) {
            Type elementType = visit(ctx.typeLiteral());
            return new ArrayType(elementType);
        }

        @Override
        public Type visitMapType(MapTypeContext ctx) {
            Type keyType = visit(ctx.key);
            Type valueType = visit(ctx.value);
            return new MapType(keyType, valueType);
        }

        @Override
        public Type visitFunctionType(FunctionTypeContext ctx) {
            List<Type> paramTypes = toTypeList(ctx.typeLiteralList());
            Type returnType = visitReturnType(ctx.returnType());
            return new FunctionType(returnType, paramTypes);
        }

        @Override
        public Type visitListType(ListTypeContext ctx) {
            Type elementType = visit(ctx.typeLiteral());
            return new ListType(elementType);
        }

        @Override
        public Type visitPrimitiveType(PrimitiveTypeContext ctx) {
            return switch (CSTNodes.getTokenType(ctx.start)) {
                case ZenScriptLexer.ANY -> AnyType.INSTANCE;
                case ZenScriptLexer.BYTE -> ByteType.INSTANCE;
                case ZenScriptLexer.SHORT -> ShortType.INSTANCE;
                case ZenScriptLexer.INT -> IntType.INSTANCE;
                case ZenScriptLexer.LONG -> LongType.INSTANCE;
                case ZenScriptLexer.FLOAT -> FloatType.INSTANCE;
                case ZenScriptLexer.DOUBLE -> DoubleType.INSTANCE;
                case ZenScriptLexer.BOOL -> BoolType.INSTANCE;
                case ZenScriptLexer.VOID -> VoidType.INSTANCE;
                case ZenScriptLexer.STRING -> StringType.INSTANCE;
                default -> null;
            };
        }

        @Override
        public Type visitClassType(ClassTypeContext ctx) {
            Scope scope = unit.getScope(unit.getParseTree());
            String qualifiedName = ctx.qualifiedName().getText();
            Symbol symbol = scope.lookupSymbol(qualifiedName);
            if (symbol != null) {
                return symbol.getType();
            } else {
                return null;
            }
        }

        @Override
        public Type visit(ParseTree node) {
            if (node != null) {
                return node.accept(this);
            } else {
                return null;
            }
        }

        @Override
        public Type visitChildren(RuleNode node) {
            return null;
        }

        private Type getListForeachVariableType(Type elementType, ForeachVariableContext variable, List<ForeachVariableContext> variables) {
            int total = variables.size();
            int index = variables.indexOf(variable);
            if (total == 1) {
                return elementType;
            }
            if (total == 2) {
                return index == 0 ? IntType.INSTANCE : elementType;
            }
            return null;
        }

        private Type getMapForeachVariableType(MapType mapType, ForeachVariableContext variable, List<ForeachVariableContext> variables) {
            int total = variables.size();
            int index = variables.indexOf(variable);
            if (total == 1) {
                return mapType.getKeyType();
            }
            if (total == 2) {
                return index == 0 ? mapType.getKeyType() : mapType.getValueType();
            }
            return null;
        }
    }

}

package raylras.zen.lsp.provider;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import raylras.zen.bracket.BracketHandlerService;
import raylras.zen.lsp.provider.data.Keywords;
import raylras.zen.lsp.provider.data.Snippet;
import raylras.zen.lsp.util.TextSimilarity;
import raylras.zen.model.CompilationUnit;
import raylras.zen.model.Document;
import raylras.zen.model.SymbolProvider;
import raylras.zen.model.Visitor;
import raylras.zen.model.parser.ZenScriptParser;
import raylras.zen.model.parser.ZenScriptParser.*;
import raylras.zen.model.resolve.TypeResolver;
import raylras.zen.model.scope.Scope;
import raylras.zen.model.symbol.*;
import raylras.zen.model.type.*;
import raylras.zen.util.Position;
import raylras.zen.util.Range;
import raylras.zen.util.*;
import raylras.zen.util.l10n.L10N;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class CompletionProvider {

    private CompletionProvider() {
    }

    public static CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(Document doc, CompletionParams params) {
        return doc.getUnit().map(unit -> CompletableFuture.supplyAsync(() -> {
            CompletionVisitor visitor = new CompletionVisitor(unit, params);
            unit.accept(visitor);
            return Either.<List<CompletionItem>, CompletionList>forLeft(visitor.completionList);
        })).orElseGet(CompletionProvider::empty);
    }

    public static CompletableFuture<Either<List<CompletionItem>, CompletionList>> empty() {
        return CompletableFuture.completedFuture(null);
    }

    private static final class CompletionVisitor extends Visitor<Void> {
        private final Position cursor;
        private final ParseTree tailing;
        private final TerminalNode leading;
        private final String text;
        private final CompilationUnit unit;
        private final List<CompletionItem> completionList = new ArrayList<>();

        private CompletionVisitor(CompilationUnit unit, CompletionParams params) {
            this.cursor = Position.of(params.getPosition());
            this.tailing = CSTNodes.getCstAtPosition(unit.getParseTree(), cursor);
            this.leading = CSTNodes.getPrevTerminal(unit.getTokenStream(), tailing);
            this.text = tailing.getText();
            this.unit = unit;
        }

        /*
            | represents the cursor
            ^ represents the leading cst node
            _ represents the tailing cst node
         */

        @Override
        public Void visitImportDeclaration(ZenScriptParser.ImportDeclarationContext ctx) {
            // import text|
            // ^^^^^^ ____
            if (containsLeading(ctx.IMPORT())) {
                completeImports(text);
                return null;
            }

            // import foo.text|
            //           ^___
            if (containsLeading(ctx.qualifiedName().DOT())) {
                String text = getTextUntilCursor(ctx.qualifiedName());
                completeImports(text);
                return null;
            }

            // import foo.|bar
            //        ^^^_
            if (containsTailing(ctx.qualifiedName().DOT())) {
                String text = getTextUntilCursor(ctx.qualifiedName());
                completeImports(text);
                return null;
            }

            // import foo.bar text|
            //            ^^^ ____
            if (!containsTailing(ctx.qualifiedName())) {
                completeKeywords(text, Keywords.AS);
                return null;
            }

            return null;
        }

        @Override
        public Void visitFormalParameter(FormalParameterContext ctx) {
            // name text|
            // ^^^^ ____
            if (containsLeading(ctx.simpleName())) {
                completeKeywords(text, Keywords.AS);
                return null;
            }

            // name as text|
            //      ^^ ____
            if (containsLeading(ctx.AS())) {
                completeTypeSymbols(text);
                return null;
            }

            return null;
        }

        @Override
        public Void visitFunctionBody(FunctionBodyContext ctx) {
            // { text| }
            // ^ ____
            if (containsLeading(ctx.BRACE_OPEN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitVariableDeclaration(VariableDeclarationContext ctx) {
            // var name text|
            //     ^^^^ ____
            if (containsLeading(ctx.simpleName())) {
                completeKeywords(text, Keywords.AS);
                return null;
            }

            // var name; text|
            //         ^ ____
            if (containsLeading(ctx.SEMICOLON())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitBlockStatement(BlockStatementContext ctx) {
            // { text| }
            // ^ ____
            if (containsLeading(ctx.BRACE_OPEN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            // { } text|
            //   ^ ____
            if (containsLeading(ctx.BRACE_CLOSE())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitReturnStatement(ReturnStatementContext ctx) {
            // return text|
            // ^^^^^^ ____
            if (containsLeading(ctx.RETURN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            // return; text|
            //       ^ ____
            if (containsLeading(ctx.SEMICOLON())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitIfStatement(IfStatementContext ctx) {
            // if text|
            // ^^ ____
            if (containsLeading(ctx.IF())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitForeachBody(ForeachBodyContext ctx) {
            // { text| }
            // ^ ____
            if (containsLeading(ctx.BRACE_OPEN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            // { } text|
            //   ^ ____
            if (containsLeading(ctx.BRACE_CLOSE())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitWhileStatement(WhileStatementContext ctx) {
            // while (|)
            // ^^^^^ _
            if (containsLeading(ctx.WHILE())) {
                completeLocalSymbols("");
                completeLocalSymbols("");
                return null;
            }

            // while (text|)
            //       ^____
            if (containsLeading(ctx.PAREN_OPEN())) {
                completeLocalSymbols(text);
                completeLocalSymbols(text);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitExpressionStatement(ExpressionStatementContext ctx) {
            // text|
            // ____
            if (ctx.expression() instanceof SimpleNameExprContext && containsTailing(ctx.expression())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            // expr; text|
            //     ^ ____
            if (containsLeading(ctx.SEMICOLON())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                completeKeywords(text, Keywords.STATEMENT);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitAssignmentExpr(AssignmentExprContext ctx) {
            // expr = text|
            //      ^ ____
            if (containsLeading(ctx.op)) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
            }

            // expr =|
            // ^^^^ _
            if (!(ctx.left instanceof MemberAccessExprContext) && containsLeading(ctx.left)) {
                completeLocalSymbols("");
                completeGlobalSymbols("");
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitBinaryExpr(BinaryExprContext ctx) {
            // expr + text|
            //      ^ ____
            if (containsLeading(ctx.op)) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
            }
            return null;
        }

        @Override
        public Void visitParensExpr(ParensExprContext ctx) {
            // (text|)
            // ^____
            if (containsLeading(ctx.PAREN_OPEN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitBracketHandlerExpr(BracketHandlerExprContext ctx) {
            completeBracketHandlers(ctx.raw().getText());
            return null;
        }

        @Override
        public Void visitUnaryExpr(UnaryExprContext ctx) {
            // !text|
            // ^____
            if (containsLeading(ctx.op)) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            return null;
        }

        @Override
        public Void visitMemberAccessExpr(MemberAccessExprContext ctx) {
            ExpressionContext expression = ctx.expression();

            // expr.text|
            //     ^____
            if (containsLeading(ctx.DOT())) {
                Type type = TypeResolver.getType(expression, unit);
                completeMembers(text, type);
                completeMemberAccessSnippets(type, ctx);
                return null;
            }

            // expr.|
            // ^^^^_
            if (containsLeading(expression)) {
                Type type = TypeResolver.getType(expression, unit);
                completeMembers("", type);
                completeMemberAccessSnippets(type, ctx);
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitCallExpr(CallExprContext ctx) {
            // expr(text|)
            //     ^____
            if (containsLeading(ctx.PAREN_OPEN())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            // expr(expr,|)
            //          ^
            if (leading instanceof ErrorNode) {
                completeLocalSymbols("");
                completeGlobalSymbols("");
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitExpressionList(ZenScriptParser.ExpressionListContext ctx) {
            // expr, text|
            //     ^ ____
            if (containsLeading(ctx.COMMA())) {
                completeLocalSymbols(text);
                completeGlobalSymbols(text);
                return null;
            }

            // expr,|
            // ^^^^_
            if (containsTailing(ctx.COMMA())) {
                completeLocalSymbols("");
                completeGlobalSymbols("");
                return null;
            }

            visitChildren(ctx);
            return null;
        }

        @Override
        public Void visitChildren(RuleNode node) {
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                if (containsLeading(child)) {
                    child.accept(this);
                    break;
                }
                if (containsTailing(child)) {
                    child.accept(this);
                    break;
                }
            }
            return null;
        }

        private boolean containsLeading(Token token) {
            return Ranges.contains(token, leading);
        }

        private boolean containsLeading(ParseTree cst) {
            return Ranges.contains(cst, leading);
        }

        private boolean containsLeading(List<? extends ParseTree> cstList) {
            for (ParseTree cst : cstList) {
                if (Ranges.contains(cst, leading)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsTailing(ParseTree cst) {
            return Ranges.contains(cst, tailing);
        }

        private boolean containsTailing(List<? extends ParseTree> cstList) {
            for (ParseTree cst : cstList) {
                if (Ranges.contains(cst, tailing)) {
                    return true;
                }
            }
            return false;
        }

        private String getTextUntilCursor(ParseTree cst) {
            Range range = Range.of(cst);
            if (range.start().line() != cursor.line()) {
                return "";
            }
            int length = cursor.column() - range.start().column();
            String text = cst.getText();
            if (length > 0) {
                return text.substring(0, length);
            }
            return "";
        }

        private void completeImports(String text) {
            PackageTree<ClassType> tree = PackageTree.of(".", unit.getEnv().getClassTypeMap());
            tree.complete(text).forEach((key, subTree) -> {
                CompletionItem completionItem = new CompletionItem(key);
                completionItem.setKind(subTree.hasElement() ? CompletionItemKind.Class : CompletionItemKind.Module);
                completionList.add(completionItem);
            });
        }

        private void completeLocalSymbols(String text) {
            Scope scope = unit.lookupScope(tailing);
            while (scope != null) {
                for (Symbol symbol : scope.getSymbols()) {
                    if (TextSimilarity.isSubsequence(text, symbol.getName())) {
                        addToCompletionList(symbol);
                    }
                }
                scope = scope.getParent();
            }
        }

        private void completeGlobalSymbols(String text) {
            unit.getEnv().getGlobalSymbols().stream()
                    .filter(symbol -> TextSimilarity.isSubsequence(text, symbol.getName()))
                    .forEach(this::addToCompletionList);
        }

        private void completeMembers(String text, Type type) {
            if (type instanceof SymbolProvider memberProvider) {
                memberProvider.withExpands(unit.getEnv()).stream()
                        .filter(symbol -> TextSimilarity.isSubsequence(text, symbol.getName()))
                        .filter(this::shouldAddedToCompletion)
                        .forEach(this::addToCompletionList);
            }
        }

        private void completeTypeSymbols(String text) {
            unit.getTopLevelSymbols().stream()
                    .filter(ImportSymbol.class::isInstance)
                    .filter(symbol -> TextSimilarity.isSubsequence(text, symbol.getName()))
                    .forEach(this::addToCompletionList);
        }

        private void completeKeywords(String text, String... keywords) {
            Arrays.stream(keywords)
                    .filter(keyword -> TextSimilarity.isSubsequence(text, keyword))
                    .forEach(this::addToCompletionList);
        }

        private void completeBracketHandlers(String text) {
            BracketHandlerService bracketService = unit.getEnv().getBracketHandlerService();
            bracketService.getEntriesLocal().stream()
                    .filter(entry -> TextSimilarity.isSubsequence(text, entry.getFirst("_id").orElse("")))
                    .forEach(entry -> {
                        CompletionItem item = new CompletionItem(entry.getFirst("_id").orElse(null));
                        item.setKind(CompletionItemKind.Value);
                        CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
                        labelDetails.setDescription(entry.getFirst("_name").orElse(""));
                        item.setLabelDetails(labelDetails);
                        item.setSortText(labelDetails.getDescription());
                        completionList.add(item);
                    });
        }

//        private void completeBracketHandler(String text, BracketHandler bracketHandler) {
//            bracketHandler.getMembers().complete(text).forEach((key, subTree) -> {
//                CompletionItem completionItem = new CompletionItem(key);
//                if (subTree.hasElement()) {
//                    completionItem.setKind(CompletionItemKind.Value);
//                    completionItem.setDetail(bracketHandler.getMemberDetails(subTree.getElement()));
//                } else {
//                    completionItem.setKind(CompletionItemKind.Module);
//                }
//                completionList.add(completionItem);
//            });
//        }

        private void addToCompletionList(Symbol symbol) {
            CompletionItem item = new CompletionItem(symbol.getName());
            item.setKind(toCompletionKind(symbol));
            item.setLabelDetails(getLabelDetails(symbol));
            if (symbol instanceof Executable executable) {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
                if (executable.getParameterList().isEmpty()) {
                    item.setInsertText(item.getLabel() + "()");
                } else {
                    item.setInsertText(item.getLabel() + "($1)");
                }
                if (executable.getReturnType() == VoidType.INSTANCE) {
                    item.setInsertText(item.getInsertText() + ";");
                }
            }
            completionList.add(item);
        }

        /**
         * @deprecated Use {@link #addToCompletionList(Symbol)} instead.
         */
        @Deprecated
        private void addToCompletionList(Symbol symbol, String detail) {
            CompletionItem item = new CompletionItem(symbol.getName());
            item.setKind(toCompletionKind(symbol));
            item.setDetail(detail);
            completionList.add(item);
        }

        private void addToCompletionList(String keyword) {
            CompletionItem item = new CompletionItem(keyword);
            item.setDetail(L10N.getString("completion.keyword"));
            item.setKind(CompletionItemKind.Keyword);
            completionList.add(item);
        }

        private CompletionItemKind toCompletionKind(Symbol symbol) {
            return switch (symbol.getKind()) {
                case IMPORT, CLASS -> CompletionItemKind.Class;
                case FUNCTION -> CompletionItemKind.Function;
                case VARIABLE, PARAMETER -> CompletionItemKind.Variable;
                default -> null;
            };
        }

        private CompletionItemKind toCompletionKind(Type type) {
            if (type instanceof ClassType) {
                return CompletionItemKind.Class;
            }
            if (type instanceof FunctionType) {
                return CompletionItemKind.Function;
            }
            return CompletionItemKind.Variable;
        }

        private boolean shouldAddedToCompletion(Symbol symbol) {
            return switch (symbol.getKind()) {
                case FUNCTION, VARIABLE, PARAMETER -> true;
                default -> false;
            };
        }

        private CompletionItemLabelDetails getLabelDetails(Symbol symbol) {
            if (symbol instanceof Executable executable) {
                CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
                String parameterList = executable.getParameterList().stream()
                        .map(param -> param.getName() + " as " + param.getType())
                        .collect(Collectors.joining(", ", "(", ")"));
                String returnType = executable.getReturnType().toString();
                labelDetails.setDetail(parameterList);
                labelDetails.setDescription(returnType);
                return labelDetails;
            } else {
                CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
                String type = symbol.getType().toString();
                labelDetails.setDescription(type);
                return labelDetails;
            }
        }

        private void completeSnippet(Snippet snippet) {
            CompletionItem completionItem = snippet.get();
            if (completionItem != null) {
                completionItem.setKind(CompletionItemKind.Snippet);
                completionList.add(completionItem);
            }
        }

        private void completeMemberAccessSnippets(Type type, MemberAccessExprContext ctx) {
            completeSnippet(Snippet.dotFor(type, unit.getEnv(), ctx));
            completeSnippet(Snippet.dotForI(type, unit.getEnv(), ctx));
            completeSnippet(Snippet.dotIfNull(type, ctx));
            completeSnippet(Snippet.dotIfNotNull(type, ctx));
            completeSnippet(Snippet.dotVal(ctx));
            completeSnippet(Snippet.dotVar(ctx));
        }
    }

}

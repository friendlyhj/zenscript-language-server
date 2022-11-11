package raylras.zen.ast;

import raylras.zen.ast.decl.*;
import raylras.zen.ast.expr.*;
import raylras.zen.ast.stmt.*;

public interface ASTNodeVisitor<T> {

    T visit(CompilationUnitNode node);

    T visit(BlockNode node);

    T visit(TypeAnnotationNode node);

    T visit(ConstructorDeclarationNode node);

    T visit(FunctionDeclarationNode node);

    T visit(ImportDeclarationNode node);

    T visit(AliasNode node);

    T visit(ParameterDeclarationNode node);

    T visit(VariableDeclarationNode node);

    T visit(ZenClassDeclarationNode node);

    T visit(BreakStatementNode node);

    T visit(ContinueStatementNode node);

    T visit(ExpressionStatementNode node);

    T visit(ForeachStatementNode node);

    T visit(IfElseStatementNode node);

    T visit(ReturnStatementNode node);

    T visit(WhileStatementNode node);

    T visit(ExpressionNode node);

    T visit(ArgumentsExpressionNode node);

    T visit(ArrayLiteralExpressionNode node);

    T visit(BinaryExpressionNode node);

    T visit(NumericLiteralNode node);

    T visit(BoolLiteralNode node);

    T visit(StringLiteralNode node);

    T visit(BracketHandlerExpressionNode node);

    T visit(FunctionExpressionNode node);

    T visit(MapEntryExpressionNode node);

    T visit(MapLiteralExpressionNode node);

    T visit(MemberAccessExpressionNode node);

    T visit(MemberIndexExpressionNode node);

    T visit(NullLiteralExpressionNode node);

    T visit(ParensExpressionNode node);

    T visit(IntRangeExpressionNode node);

    T visit(TernaryExpressionNode node);

    T visit(ThisExpressionNode node);

    T visit(TypeAssertionExpressionNode node);

    T visit(UnaryExpressionNode node);

    T visit(TypeNameNode node);

}

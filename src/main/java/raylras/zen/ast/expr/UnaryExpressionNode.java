package raylras.zen.ast.expr;

import raylras.zen.ast.ASTNode;
import raylras.zen.ast.ASTNodeVisitor;

public class UnaryExpressionNode extends ASTNode implements ExpressionNode {

    private ExpressionNode expr;
    private Operator operator;

    public UnaryExpressionNode(Operator operator) {
        this.operator = operator;
    }

    public ExpressionNode getExpr() {
        return expr;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public <T> T accept(ASTNodeVisitor<? extends T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void addChild(ASTNode node) {
        if (node instanceof ExpressionNode) {
            if (expr == null) {
                expr = (ExpressionNode) node;
            }
        }
    }

    @Override
    public String toString() {
        return operator.toString() + expr;
    }

}

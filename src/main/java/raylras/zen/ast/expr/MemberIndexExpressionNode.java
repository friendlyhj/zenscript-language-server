package raylras.zen.ast.expr;

import raylras.zen.ast.ASTNode;
import raylras.zen.ast.ASTNodeVisitor;

/**
 * arr[1]
 */
public class MemberIndexExpressionNode extends ASTNode implements ExpressionNode {

    private ExpressionNode left;
    private ExpressionNode index;

    public MemberIndexExpressionNode() {
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getIndex() {
        return index;
    }

    @Override
    public <T> T accept(ASTNodeVisitor<? extends T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void addChild(ASTNode node) {
        if (node instanceof ExpressionNode) {
            if (left == null) {
                left = (ExpressionNode) node;
            } else if (index == null) {
                index = (ExpressionNode) node;
            }
        }
    }

    @Override
    public String toString() {
        return left + "[" + index + "]";
    }

}

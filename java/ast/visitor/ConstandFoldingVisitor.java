package ast.visitor;

import ast.*;
import compiler.Scope;

public class ConstantFoldingVisitor extends AbstractASTVisitor<ASTNode> {

    @Override
    protected ASTNode postprocess(BinaryOpNode node, ASTNode left, ASTNode right) {
        // Check if left and right are IntLitNode or FloatLitNode
        if ((left instanceof IntLitNode) && (right instanceof IntLitNode)) {
            int lval = ((IntLitNode) left).getVal();
            int rval = ((IntLitNode) right).getVal();
            int result;
            switch (node.getOp()) {
                case ADD: result = lval + rval; break;
                case SUB: result = lval - rval; break;
                case MUL: result = lval * rval; break;
                case DIV: 
                    if (rval == 0) return node; //can't fold division by zero
                    result = lval / rval; 
                    break;
                default: return node;
            }
            return new IntLitNode(String.valueOf(result));
        } else if ((left instanceof FloatLitNode) && (right instanceof FloatLitNode)) {
            float lval = ((FloatLitNode) left).getVal();
            float rval = ((FloatLitNode) right).getVal();
            float result;
            switch (node.getOp()) {
                case ADD: result = lval + rval; break;
                case SUB: result = lval - rval; break;
                case MUL: result = lval * rval; break;
                case DIV: 
                    if (rval == 0.0f) return node; // can't fold division by zero
                    result = lval / rval; 
                    break;
                default: return node;
            }
            return new FloatLitNode(String.valueOf(result));
        } 
        // If one is float and the other is int, we can still fold, converting int->float
        else if ((left instanceof IntLitNode) && (right instanceof FloatLitNode)) {
            float lval = (float)((IntLitNode) left).getVal();
            float rval = ((FloatLitNode) right).getVal();
            float result;
            switch (node.getOp()) {
                case ADD: result = lval + rval; break;
                case SUB: result = lval - rval; break;
                case MUL: result = lval * rval; break;
                case DIV:
                    if (rval == 0.0f) return node;
                    result = lval / rval;
                    break;
                default: return node;
            }
            return new FloatLitNode(String.valueOf(result));
        } else if ((left instanceof FloatLitNode) && (right instanceof IntLitNode)) {
            float lval = ((FloatLitNode) left).getVal();
            float rval = (float)((IntLitNode) right).getVal();
            float result;
            switch (node.getOp()) {
                case ADD: result = lval + rval; break;
                case SUB: result = lval - rval; break;
                case MUL: result = lval * rval; break;
                case DIV:
                    if (rval == 0.0f) return node;
                    result = lval / rval;
                    break;
                default: return node;
            }
            return new FloatLitNode(String.valueOf(result));
        }

        return node; // If not both constants, return the original node
    }

    // For all other nodes, just return them unchanged
}

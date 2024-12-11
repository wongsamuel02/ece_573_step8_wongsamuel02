package assembly;

import java.util.List;

import compiler.Scope.SymbolTableEntry;
import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import compiler.Scope;
import compiler.LocalScope;

public class CodeGenerator extends AbstractASTVisitor<CodeObject> {

	int intRegCount;
	int floatRegCount;
	static final public char intTempPrefix = 't';
	static final public char floatTempPrefix = 'f';
	
	int loopLabel;
	int elseLabel;
	int outLabel;

	String currFunc;
	
	public CodeGenerator() {
		loopLabel = 0;
		elseLabel = 0;
		outLabel = 0;
		intRegCount = 0;		
		floatRegCount = 0;
	}

	public int getIntRegCount() {
		return intRegCount;
	}

	public int getFloatRegCount() {
		return floatRegCount;
	}
	
	/**
	 * Generate code for Variables
	 * 
	 * Create a code object that just holds a variable
	 * 
	 * Important: add a pointer from the code object to the symbol table entry
	 *            so we know how to generate code for it later (we'll need to find
	 *            the address)
	 * 
	 * Mark the code object as holding a variable, and also as an lval
	 */
	@Override
	protected CodeObject postprocess(VarNode node) {
		
		Scope.SymbolTableEntry sym = node.getSymbol();
		
		CodeObject co = new CodeObject(sym);
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/** Generate code for IntLiterals
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(IntLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a register
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new Li(generateTemp(Scope.InnerType.INT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/** Generate code for FloatLiteras
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(FloatLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a regisster
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new FImm(generateTemp(Scope.InnerType.FLOAT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for binary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from left child
	 * Step 1a: if left child is an lval, add a load to get the data
	 * Step 2: add code from right child
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate binary operation using temps from left and right
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(BinaryOpNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();
		Instruction i = null;

		// 1. Rvalify left if needed and add its code
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.code);

		// 2. Rvalify right if needed and add its code
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);

		// Determine the result type before possibly adjusting for floats
		Scope.InnerType resultType = node.getType().type;

		// Implicit type conversion from INT to FLOAT if needed
		// If one operand is float and the other is int, convert int to float
		if (left.type.type == Scope.InnerType.INT && right.type.type == Scope.InnerType.FLOAT) {
			resultType = Scope.InnerType.FLOAT;
			co.code.add(new IMovF(left.temp, generateTemp(Scope.InnerType.FLOAT)));
			left.temp = co.code.getLast().getDest();
		} else if (left.type.type == Scope.InnerType.FLOAT && right.type.type == Scope.InnerType.INT) {
			resultType = Scope.InnerType.FLOAT;
			co.code.add(new IMovF(right.temp, generateTemp(Scope.InnerType.FLOAT)));
			right.temp = co.code.getLast().getDest();
		}

		// Generate the appropriate instruction based on operation and result type
		switch (resultType) {
			case INT:
				i = generateIntBinaryOp(node.getOp(), left.temp, right.temp);
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			case FLOAT:
				i = generateFloatBinaryOp(node.getOp(), left.temp, right.temp);
				co.type = new Scope.Type(Scope.InnerType.FLOAT);
				break;
			case PTR: // Pointer arithmetic treated like INT for now
				i = generateIntBinaryOp(node.getOp(), left.temp, right.temp);
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			default:
				throw new RuntimeException("Unsupported type in BinaryOpNode: " + resultType);
		}

		co.code.add(i);
		co.lval = false;
		co.temp = i.getDest();

		return co;
	}

	// Helper methods to generate instructions for int and float ops:
	private Instruction generateIntBinaryOp(BinaryOpNode.OpType op, String leftTemp, String rightTemp) {
		switch (op) {
			case ADD: return new Add(leftTemp, rightTemp, generateTemp(Scope.InnerType.INT));
			case SUB: return new Sub(leftTemp, rightTemp, generateTemp(Scope.InnerType.INT));
			case MUL: return new Mul(leftTemp, rightTemp, generateTemp(Scope.InnerType.INT));
			case DIV: return new Div(leftTemp, rightTemp, generateTemp(Scope.InnerType.INT));
			default: throw new RuntimeException("Unknown int binary operation: " + op);
		}
	}

	private Instruction generateFloatBinaryOp(BinaryOpNode.OpType op, String leftTemp, String rightTemp) {
		switch (op) {
			case ADD: return new FAdd(leftTemp, rightTemp, generateTemp(Scope.InnerType.FLOAT));
			case SUB: return new FSub(leftTemp, rightTemp, generateTemp(Scope.InnerType.FLOAT));
			case MUL: return new FMul(leftTemp, rightTemp, generateTemp(Scope.InnerType.FLOAT));
			case DIV: return new FDiv(leftTemp, rightTemp, generateTemp(Scope.InnerType.FLOAT));
			default: throw new RuntimeException("Unknown float binary operation: " + op);
		}
	}


	/**
	 * Generate code for unary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from child expression
	 * Step 1a: if child is an lval, add a load to get the data
	 * Step 2: generate instruction to perform unary operation
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(UnaryOpNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		// 1. Rvalify if needed
		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		// 2. Generate unary operation instruction (neg or fneg)
		Instruction negInstr = null;
		switch (node.getType().type) {
			case INT:
				negInstr = new Neg(expr.temp, generateTemp(Scope.InnerType.INT));
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			case FLOAT:
				negInstr = new FNeg(expr.temp, generateTemp(Scope.InnerType.FLOAT));
				co.type = new Scope.Type(Scope.InnerType.FLOAT);
				break;
			default:
				throw new RuntimeException("Unsupported type in UnaryOpNode: " + node.getType().type);
		}

		co.code.add(negInstr);
		co.temp = negInstr.getDest();
		co.lval = false;

		return co;
	}

	/**
	 * Generate code for assignment statements
	 * 
	 * Step 0: create new code object
	 * Step 1: if LHS is a variable, generate a load instruction to get the address into a register
	 * Step 1a: add code from LHS of assignment (make sure it results in an lval!)
	 * Step 2: add code from RHS of assignment
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate store
	 * 
	 * Hint: it is going to be easiest to just generate a store with a 0 immediate
	 * offset, and the complete store address in a register:
	 * 
	 * sw rhs 0(lhs)
	 */
	@Override
	protected CodeObject postprocess(AssignNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();

		// If LHS is a variable but not local, we need its address; if it's an lval in other contexts, code should handle it.
		// If LHS is not a variable, we assume it's already an address.
		SymbolTableEntry symbol = left.getSTE();
		boolean isGlobal = symbol != null && !symbol.isLocal();
		boolean lhsIsVar = left.isVar();

		// 1. If LHS is an lval, just add its code
		co.code.addAll(left.code);

		// 2. Rvalify right if needed
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);

		// Implicit type conversion
		Scope.InnerType lhsType = left.type.type;
		Scope.InnerType rhsType = right.type.type;
		Scope.InnerType finalType = lhsType;

		if (lhsType == Scope.InnerType.INT && rhsType == Scope.InnerType.FLOAT) {
			co.code.add(new FMovI(right.temp, generateTemp(Scope.InnerType.INT)));
			right.temp = co.code.getLast().getDest();
			finalType = Scope.InnerType.INT;
		} else if (lhsType == Scope.InnerType.FLOAT && rhsType == Scope.InnerType.INT) {
			co.code.add(new IMovF(right.temp, generateTemp(Scope.InnerType.FLOAT)));
			right.temp = co.code.getLast().getDest();
			finalType = Scope.InnerType.FLOAT;
		}

		// If dealing with pointers, treat them like INT for storage
		if (lhsType == Scope.InnerType.PTR) {
			finalType = Scope.InnerType.INT;
		}

		// For global variables, generate their address if needed
		InstructionList varAddrInstr = null;
		if (lhsIsVar && isGlobal) {
			varAddrInstr = generateAddrFromVariable(left);
			co.code.addAll(varAddrInstr);
		}

		// 3. Generate store based on the finalType
		Instruction storeInstr = generateStoreInstruction(finalType, right.temp,
				lhsIsVar, isGlobal, symbol, varAddrInstr, left.temp);

		co.code.add(storeInstr);
		co.type = new Scope.Type(finalType);
		co.lval = false; // After assignment, we do not hold an address or value specifically

		return co;
	}

	// Helper method to generate store instructions for assign
	private Instruction generateStoreInstruction(Scope.InnerType type, String srcTemp, boolean lhsIsVar,
												boolean isGlobal, SymbolTableEntry symbol,
												InstructionList varAddrInstr, String lhsTemp) {
		if (lhsIsVar && symbol != null) {
			String address = symbol.addressToString();
			if (isGlobal) {
				// Global store: use the address computed in varAddrInstr (dest is last's dest)
				String addrReg = varAddrInstr.getLast().getDest();
				return (type == Scope.InnerType.FLOAT) ? new Fsw(srcTemp, addrReg, "0") : new Sw(srcTemp, addrReg, "0");
			} else {
				// Local store: store directly with fp + offset
				return (type == Scope.InnerType.FLOAT) ? new Fsw(srcTemp, "fp", address) : new Sw(srcTemp, "fp", address);
			}
		} else {
			// lhs is not a var, so lhsTemp is an address in a register
			return (type == Scope.InnerType.FLOAT) ? new Fsw(srcTemp, lhsTemp, "0") : new Sw(srcTemp, lhsTemp, "0");
		}
	}

	/**
	 * Type cast expression:
		issue instructions to convert int -> float and float -> int
	 */
	@Override
	protected CodeObject postprocess(CastExprNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		// If expr is an lval, convert it to an rval
		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		Scope.Type castType = node.getType();
		Scope.InnerType fromType = expr.type.type;
		Scope.InnerType toType = castType.type;

		// Perform type conversion if needed
		if (toType == Scope.InnerType.INT && fromType == Scope.InnerType.FLOAT) {
			co.code.add(new FMovI(expr.temp, generateTemp(Scope.InnerType.INT)));
			co.temp = co.code.getLast().getDest();
			co.type = new Scope.Type(Scope.InnerType.INT);
			co.lval = false;
		} else if (toType == Scope.InnerType.FLOAT && fromType == Scope.InnerType.INT) {
			co.code.add(new IMovF(expr.temp, generateTemp(Scope.InnerType.FLOAT)));
			co.temp = co.code.getLast().getDest();
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
			co.lval = false;
		} else {
			// If no conversion is needed, just pass through
			co.temp = expr.temp;
			co.type = expr.type;
			co.lval = expr.lval;
		}

		return co;
	}


	

	/**
	 * Add together all the lists of instructions generated by the children
	 */
	@Override
	protected CodeObject postprocess(StatementListNode node, List<CodeObject> statements) {
		CodeObject co = new CodeObject();
		// Concatenate the code from each statement
		for (CodeObject stmt : statements) {
			co.code.addAll(stmt.code);
		}
		// Statement lists do not produce a value
		co.type = new Scope.Type(Scope.InnerType.VOID);;
		co.temp = null;
		co.lval = false;
		return co;
	}

	
	/**
	 * Generate code for read
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from VarNode (make sure it's an lval)
	 * Step 2: generate GetI instruction, storing into temp
	 * Step 3: generate store, to store temp in variable
	 */
	@Override
	protected CodeObject postprocess(ReadNode node, CodeObject var) {
		CodeObject co = new CodeObject();

		// var must be a variable
		assert(var.getSTE() != null);

		Instruction inputInstr = null;
		InstructionList storeInstrs = new InstructionList();
		boolean isLocal = var.getSTE().isLocal();
		String addr = var.getSTE().addressToString();

		// Generate input instruction (geti or getf)
		switch (node.getType().type) {
			case INT:
				inputInstr = new GetI(generateTemp(Scope.InnerType.INT));
				co.code.add(inputInstr);
				if (isLocal) {
					storeInstrs.add(new Sw(inputInstr.getDest(), "fp", addr));
				} else {
					storeInstrs.addAll(generateAddrFromVariable(var));
					storeInstrs.add(new Sw(inputInstr.getDest(), storeInstrs.getLast().getDest(), "0"));
				}
				co.code.addAll(storeInstrs);
				break;

			case FLOAT:
				inputInstr = new GetF(generateTemp(Scope.InnerType.FLOAT));
				co.code.add(inputInstr);
				if (isLocal) {
					storeInstrs.add(new Fsw(inputInstr.getDest(), "fp", addr));
				} else {
					storeInstrs.addAll(generateAddrFromVariable(var));
					storeInstrs.add(new Fsw(inputInstr.getDest(), storeInstrs.getLast().getDest(), "0"));
				}
				co.code.addAll(storeInstrs);
				break;

			default:
				throw new Error("Shouldn't read into other variable types");
		}

		// After a read, no meaningful rval
		co.lval = false;
		co.temp = null;
		co.type = new Scope.Type(Scope.InnerType.VOID);

		return co;
	}


	/**
	 * Generate code for print
	 * 
	 * Step 0: create new code object
	 * 
	 * If printing a string:
	 * Step 1: add code from expression to be printed (make sure it's an lval)
	 * Step 2: generate a PutS instruction printing the result of the expression
	 * 
	 * If printing an integer:
	 * Step 1: add code from the expression to be printed
	 * Step 1a: if it's an lval, generate a load to get the data
	 * Step 2: Generate PutI that prints the temporary holding the expression
	 */
	@Override
	protected CodeObject postprocess(WriteNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		Scope.InnerType writeType = node.getWriteExpr().getType().type;

		if (writeType == Scope.InnerType.STRING) {
			// Writing a string requires getting its address and calling PutS
			assert(expr.getSTE() != null);
			InstructionList addrInstrs = generateAddrFromVariable(expr);
			co.code.addAll(addrInstrs);
			co.code.add(new PutS(addrInstrs.getLast().getDest()));
		} else {
			// If it's an lval, convert it to rval before printing
			if (expr.lval) {
				expr = rvalify(expr);
			}
			co.code.addAll(expr.code);

			// Choose the correct print instruction based on type
			Instruction printInstr;
			switch (writeType) {
				case INT:
					printInstr = new PutI(expr.temp);
					break;
				case FLOAT:
					printInstr = new PutF(expr.temp);
					break;
				default:
					throw new Error("WriteNode: Unexpected type " + writeType);
			}
			co.code.add(printInstr);
		}

		// After writing, no value is produced
		co.lval = false;
		co.temp = null;
		co.type = new Scope.Type(Scope.InnerType.VOID);

		return co;
	}


	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Generating an instruction sequence for a conditional expression
	 * 
	 * Implement this however you like. One suggestion:
	 *
	 * Create the code for the left and right side of the conditional, but defer
	 * generating the branch until you process IfStatementNode or WhileNode (since you
	 * do not know the labels yet). Modify CodeObject so you can save the necessary
	 * information to generate the branch instruction in IfStatementNode or WhileNode
	 * 
	 * Alternate idea 1:
	 * 
	 * Don't do anything as part of CodeGenerator. Create a new visitor class
	 * that you invoke *within* your processing of IfStatementNode or WhileNode
	 * 
	 * Alternate idea 2:
	 * 
	 * Create the branch instruction in this function, then tweak it as necessary in
	 * IfStatementNode or WhileNode
	 * 
	 * Hint: you may need to preserve extra information in the returned CodeObject to
	 * make sure you know the type of branch code to generate (int vs float)
	 */
	@Override
	protected CodeObject postprocess(CondNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();

		// Convert lvals to rvals if needed
		if (left.lval) left = rvalify(left);
		if (right.lval) right = rvalify(right);

		// Add code from children
		co.code.addAll(left.code);
		co.code.addAll(right.code);

		// Handle implicit type conversions (int <-> float)
		Scope.InnerType leftType = left.type.type;
		Scope.InnerType rightType = right.type.type;

		if (leftType == Scope.InnerType.INT && rightType == Scope.InnerType.FLOAT) {
			co.code.add(new IMovF(left.temp, generateTemp(Scope.InnerType.FLOAT)));
			co.leftTemp = co.code.getLast().getDest();
			co.rightTemp = right.temp;
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
		} else if (leftType == Scope.InnerType.FLOAT && rightType == Scope.InnerType.INT) {
			co.code.add(new IMovF(right.temp, generateTemp(Scope.InnerType.FLOAT)));
			co.leftTemp = left.temp;
			co.rightTemp = co.code.getLast().getDest();
			co.type = new Scope.Type(Scope.InnerType.FLOAT);
		} else {
			// If both are same type, just pass through
			co.leftTemp = left.temp;
			co.rightTemp = right.temp;
			co.type = node.getLeft().getType();
		}

		return co;
	}


	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		<cond code>
	 *		<flipped branch> elseLabel
	 *		<then code>
	 *		j outLabel
	 *		elseLabel:
	 *		<else code>
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(IfStatementNode node, CodeObject cond, CodeObject tlist, CodeObject elist) {
		CodeObject co = new CodeObject();

		// Generate labels
		String elseLabel = generateElseLabel();
		String outLabel = generateOutLabel();

		// Add condition code
		co.code.addAll(cond.code);

		CondNode cnode = node.getCondExpr();

		// Determine if we have an else part or not
		boolean hasElse = !elist.code.isEmpty();

		Instruction branchCond = null;
		Instruction floatCmp = null;

		// If condition type is INT
		if (cond.type.type == Scope.InnerType.INT) {
			// Generate an integer branch based on the reversed op
			switch (cnode.getReversedOp()) {
				case LE: branchCond = new Ble(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
				case LT: branchCond = new Blt(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
				case GE: branchCond = new Bge(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
				case GT: branchCond = new Bgt(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
				case EQ: branchCond = new Beq(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
				case NE: branchCond = new Bne(cond.leftTemp, cond.rightTemp, hasElse ? elseLabel : outLabel); break;
			}
			co.code.add(branchCond);

		} else if (cond.type.type == Scope.InnerType.FLOAT) {
			// Generate a float comparison first, then a branch
			switch (cnode.getReversedOp()) {
				case LE:
					floatCmp = new Fle(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
				case LT:
					floatCmp = new Flt(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
				case GE:
					floatCmp = new Flt(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
				case GT:
					floatCmp = new Fle(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
				case EQ:
					floatCmp = new Feq(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
				case NE:
					floatCmp = new Feq(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", hasElse ? elseLabel : outLabel);
					break;
			}
			co.code.add(floatCmp);
			co.code.add(branchCond);
		}

		// If we have no else part
		if (!hasElse) {
			// then-part
			co.code.addAll(tlist.code);
			// outLabel
			co.code.add(new Label(outLabel));
		} else {
			// If we have else part
			co.code.addAll(tlist.code);
			co.code.add(new J(outLabel));
			co.code.add(new Label(elseLabel));
			co.code.addAll(elist.code);
			co.code.add(new Label(outLabel));
		}

		return co;
	}


		/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		loopLabel:
	 *		<cond code>
	 *		<flipped branch> outLabel
	 *		<body code>
	 *		j loopLabel
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(WhileNode node, CodeObject cond, CodeObject slist) {
		CodeObject co = new CodeObject();

		// Generate labels
		String loopLabel = generateLoopLabel();
		String outLabel = generateOutLabel();

		// loopLabel:
		co.code.add(new Label(loopLabel));

		// Add condition code
		co.code.addAll(cond.code);

		CondNode cnode = node.getCond();
		Instruction branchCond = null;
		Instruction floatCmp = null;

		// Generate the branch based on condition type
		if (cond.type.type == Scope.InnerType.INT) {
			switch (cnode.getReversedOp()) {
				case LE: branchCond = new Ble(cond.leftTemp, cond.rightTemp, outLabel); break;
				case LT: branchCond = new Blt(cond.leftTemp, cond.rightTemp, outLabel); break;
				case GE: branchCond = new Bge(cond.leftTemp, cond.rightTemp, outLabel); break;
				case GT: branchCond = new Bgt(cond.leftTemp, cond.rightTemp, outLabel); break;
				case EQ: branchCond = new Beq(cond.leftTemp, cond.rightTemp, outLabel); break;
				case NE: branchCond = new Bne(cond.leftTemp, cond.rightTemp, outLabel); break;
			}
			co.code.add(branchCond);

		} else if (cond.type.type == Scope.InnerType.FLOAT) {
			// For float, perform a comparison first, then branch
			switch (cnode.getReversedOp()) {
				case LE:
					floatCmp = new Fle(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", outLabel);
					break;
				case LT:
					floatCmp = new Flt(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", outLabel);
					break;
				case GE:
					floatCmp = new Flt(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", outLabel);
					break;
				case GT:
					floatCmp = new Fle(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", outLabel);
					break;
				case EQ:
					floatCmp = new Feq(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Bne(floatCmp.getDest(), "x0", outLabel);
					break;
				case NE:
					floatCmp = new Feq(cond.leftTemp, cond.rightTemp, generateTemp(Scope.InnerType.INT));
					branchCond = new Beq(floatCmp.getDest(), "x0", outLabel);
					break;
			}
			co.code.add(floatCmp);
			co.code.add(branchCond);
		}

		// body code
		co.code.addAll(slist.code);

		// Jump back to loopLabel
		co.code.add(new J(loopLabel));

		// outLabel:
		co.code.add(new Label(outLabel));

		return co;
	}


	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generating code for returns
	 * 
	 * Step 0: Generate new code object
	 * 
	 * Step 1: Add retExpr code to code object (rvalify if necessary)
	 * 
	 * Step 2: Store result of retExpr in appropriate place on stack (fp + 8)
	 * 
	 * Step 3: Jump to out label (use @link{generateFunctionOutLabel()})
	 */
	@Override
	protected CodeObject postprocess(ReturnNode node, CodeObject retExpr) {
		CodeObject co = new CodeObject();

		if (retExpr == null) {
			// void return
			co.code.add(new J(generateFunctionOutLabel()));
			co.lval = false;
			co.temp = null;
			// Set type to VOID instead of leaving null
			co.type = new Scope.Type(Scope.InnerType.VOID);
			return co;
		}

		if (retExpr.lval) {
			retExpr = rvalify(retExpr);
		}
		co.code.addAll(retExpr.code);

		// Store return value
		Instruction store;
		switch (retExpr.getType().type) {
			case INT:
			case PTR:
				store = new Sw(retExpr.temp, "fp", "8");
				co.type = new Scope.Type(Scope.InnerType.INT); // Ensure co.type is set
				break;
			case FLOAT:
				store = new Fsw(retExpr.temp, "fp", "8");
				co.type = new Scope.Type(Scope.InnerType.FLOAT); // Ensure co.type is set
				break;
			default:
				throw new Error("Unsupported return type in ReturnNode");
		}
		co.code.add(store);
		co.code.add(new J(generateFunctionOutLabel()));

		co.lval = false;
		co.temp = null;
		return co;
	}



	@Override
	protected void preprocess(FunctionNode node) {
		// Generate function label information, used for other labels inside function
		currFunc = node.getFuncName();

		//reset register counts; each function uses new registers!
		intRegCount = 0;
		floatRegCount = 0;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generate code for functions
	 * 
	 * Step 1: add the label for the beginning of the function
	 * 
	 * Step 2: manage frame  pointer
	 * 			a. Save old frame pointer
	 * 			b. Move frame pointer to point to base of activation record (current sp)
	 * 			c. Update stack pointer
	 * 
	 * Step 3: allocate new stack frame (use scope infromation from FunctionNode)
	 * 
	 * Step 4: save registers on stack (Can inspect intRegCount and floatRegCount to know what to save)
	 * 
	 * Step 5: add the code from the function body
	 * 
	 * Step 6: add post-processing code:
	 * 			a. Label for `return` statements inside function body to jump to
	 * 			b. Restore registers
	 * 			c. Deallocate stack frame (set stack pointer to frame pointer)
	 * 			d. Reset fp to old location
	 * 			e. Return from function
	 */
	@Override
	protected CodeObject postprocess(FunctionNode node, CodeObject body) {
		CodeObject co = new CodeObject();

		co.code.add(new Label(generateFunctionLabel(node.getFuncName())));

		co.code.add(new Sw("fp", "sp", "0"));
		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Addi("sp", "-4", "sp"));

		int numLocals = node.getScope().getNumLocals();
		co.code.add(new Addi("sp", String.valueOf(-4 * numLocals), "sp"));

		saveRegisters(co, "t", getIntRegCount());
		saveRegisters(co, "f", getFloatRegCount(), true);

		co.code.addAll(body.code);

		co.code.add(new Label(generateFunctionOutLabel()));

		restoreRegisters(co, "f", getFloatRegCount(), true);
		restoreRegisters(co, "t", getIntRegCount());

		co.code.add(new Mv("fp", "sp"));
		co.code.add(new Lw("fp", "fp", "0"));

		co.code.add(new Ret());

		return co;
	}

	private void saveRegisters(CodeObject co, String prefix, int count) {
		saveRegisters(co, prefix, count, false);
	}

	private void saveRegisters(CodeObject co, String prefix, int count, boolean isFloat) {
		for (int i = 1; i <= count; i++) {
			String reg = prefix + i;
			if (isFloat) {
				co.code.add(new Fsw(reg, "sp", "0"));
			} else {
				co.code.add(new Sw(reg, "sp", "0"));
			}
			co.code.add(new Addi("sp", "-4", "sp"));
		}
	}

	private void restoreRegisters(CodeObject co, String prefix, int count) {
		restoreRegisters(co, prefix, count, false);
	}

	private void restoreRegisters(CodeObject co, String prefix, int count, boolean isFloat) {
		for (int i = count; i >= 1; i--) {
			String reg = prefix + i;
			co.code.add(new Addi("sp", "4", "sp"));
			if (isFloat) {
				co.code.add(new Flw(reg, "sp", "0"));
			} else {
				co.code.add(new Lw(reg, "sp", "0"));
			}
		}
	}

	/**
	 * Generate code for the list of functions. This is the "top level" code generation function
	 * 
	 * Step 1: Set fp to point to sp
	 * 
	 * Step 2: Insert a JR to main
	 * 
	 * Step 3: Insert a HALT
	 * 
	 * Step 4: Include all the code of the functions
	 */
	@Override
	protected CodeObject postprocess(FunctionListNode node, List<CodeObject> funcs) {
		CodeObject co = new CodeObject();

		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Jr(generateFunctionLabel("main")));
		co.code.add(new Halt());
		co.code.add(new Blank());

		for (CodeObject c : funcs) {
			co.code.addAll(c.code);
			co.code.add(new Blank());
		}

		return co;
	}

	/**
	* 
	* FILL IN FOR STEP 4
	* 
	* Generate code for a call expression
	 * 
	 * Step 1: For each argument:
	 * 
	 * 	Step 1a: insert code of argument (don't forget to rvalify!)
	 * 
	 * 	Step 1b: push result of argument onto stack 
	 * 
	 * Step 2: alloate space for return value
	 * 
	 * Step 3: push current return address onto stack
	 * 
	 * Step 4: jump to function
	 * 
	 * Step 5: pop return address back from stack
	 * 
	 * Step 6: pop return value into fresh temporary (destination of call expression)
	 * 
	 * Step 7: remove arguments from stack (move sp)
	 */
	@Override
	protected CodeObject postprocess(CallNode node, List<CodeObject> args) {
		CodeObject co = new CodeObject();
		int i = 0;
		Instruction store = null;

		for (CodeObject arg : args) {
			if (arg.lval)
				arg = rvalify(arg);
			co.code.addAll(arg.code);

			if (arg.getType().type == Scope.InnerType.INT) {
				store = new Sw(arg.temp, "sp", "0"); co.code.add(store);
				store = new Addi("sp", "-4", "sp"); co.code.add(store);
			} else if (arg.getType().type == Scope.InnerType.FLOAT) {
				store = new Fsw(arg.temp, "sp", "0"); co.code.add(store);
				store = new Addi("sp", "-4", "sp"); co.code.add(store);
			} else if (arg.getType().type == Scope.InnerType.PTR) {
				store = new Sw(arg.temp, "sp", "0"); co.code.add(store);
				store = new Addi("sp", "-4", "sp"); co.code.add(store);
			}

			i = i - 4;
		}

		store = new Addi("sp", "-4", "sp");
		co.code.add(store);

		store = new Sw("ra", "sp", "0");
		co.code.add(store);
		store = new Addi("sp", "-4", "sp");
		co.code.add(store);

		store = new Jr(generateFunctionLabel(node.getFuncName()));
		co.code.add(store);

		store = new Addi("sp", "4", "sp");
		co.code.add(store);
		store = new Lw("ra", "sp", "0");
		co.code.add(store);

		store = new Addi("sp", "4", "sp");
		co.code.add(store);

		if (node.getType().type != Scope.InnerType.VOID) {
			System.out.println("CallNode: ret type: " + node.getType().type);

			if (node.getType().type == Scope.InnerType.INT) {
				store = new Lw(generateTemp(Scope.InnerType.INT), "sp", "0");
			} else if (node.getType().type == Scope.InnerType.FLOAT) {
				store = new Flw(generateTemp(Scope.InnerType.FLOAT), "sp", "0");
			} else if (node.getType().type == Scope.InnerType.PTR) {
				store = new Lw(generateTemp(Scope.InnerType.INT), "sp", "0");
			}

			co.code.add(store);
			co.temp = store.getDest();
		}

		i = -1 * i;
		store = new Addi("sp", String.valueOf(i), "sp");
		co.code.add(store);

		return co;
	}


	/**
	 * Generate code for * (expr)
	 * 
	 * Goal: convert the r-val coming from expr (a computed address) into an l-val (an address that can be loaded/stored)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Rvalify expr if needed
	 * 
	 * Step 2: Copy code from expr (including any rvalification) into new code object
	 * 
	 * Step 3: New code object has same temporary as old code, but now is marked as an l-val
	 * 
	 * Step 4: New code object has an "unwrapped" type: if type of expr is * T, type of temporary is T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(PtrDerefNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.lval) {
			expr = rvalify(expr);
		}

		co.code.addAll(expr.code);
		co.temp = expr.temp;
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for a & (expr)
	 * 
	 * Goal: convert the lval coming from expr (an address) to an r-val (a piece of data that can be used)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: If lval is a variable, generate code to put address into a register (e.g., generateAddressFromVar)
	 *			Otherwise just copy code from other code object
	 * 
	 * Step 2: New code object has same temporary as existing code, but is an r-val
	 * 
	 * Step 3: New code object has a "wrapped" type. If type of expr is T, type of temporary is *T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(AddrOfNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		// Step 1: Determine if the expression is a variable
		if (expr.isVar()) {
			InstructionList addressInstructions = generateAddrFromVariable(expr);
			co.code.addAll(addressInstructions);
			co.temp = addressInstructions.getLast().getDest();
		} else {
			co.code.addAll(expr.code);
			co.temp = expr.temp;
		}

		// Step 2: Set lval field to false
		co.lval = false;

		// Step 3: Set the type of the CodeObject
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for malloc
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new MALLOC instruction
	 * 
	 * Step 3: Set code object type to INFER
	 */
	@Override
	protected CodeObject postprocess(MallocNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		Instruction mallocInstruction = new Malloc(expr.temp, generateTemp(Scope.InnerType.INT));
		co.code.add(mallocInstruction);

		co.type = new Scope.Type(Scope.InnerType.INFER);
		co.temp = mallocInstruction.getDest();

		return co;
	}
	
	/**
	 * Generate code for free
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new FREE instruction
	 */
	@Override
	protected CodeObject postprocess(FreeNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		Instruction freeInstruction = new Free(expr.temp);
		co.code.add(freeInstruction);

		return co;
	}


	
	/**
	 * Generate a fresh temporary
	 * 
	 * @return new temporary register name
	 */
	protected String generateTemp(Scope.InnerType t) {
		switch(t) {
			case INT: return intTempPrefix + String.valueOf(++intRegCount);
			case FLOAT: return floatTempPrefix + String.valueOf(++floatRegCount);
			default: throw new Error("Generating temp for bad type");
		}
	}

	protected String generateLoopLabel() {
		return "loop_" + String.valueOf(++loopLabel);
	}

	protected String generateElseLabel() {
		return  "else_" + String.valueOf(++elseLabel);
	}

	protected String generateOutLabel() {
		return "out_" +  String.valueOf(++outLabel);
	}

	protected String generateFunctionLabel() {
		return "func_" + currFunc;
	}

	protected String generateFunctionLabel(String func) {
		return "func_" + func;
	}

	protected String generateFunctionOutLabel() {
		return "func_ret_" + currFunc;
	}
	
	/**
	 * Take a code object that results in an lval, and create a new code
	 * object that adds a load to generate the rval.
	 * 
	 * @param lco The code object resulting in an address
	 * @return A code object with all the code of <code>lco</code> followed by a load
	 *         to generate an rval
	 */
	protected CodeObject rvalify(CodeObject lco) {
		assert (lco.lval);

		CodeObject co = new CodeObject();
		Instruction load = null;
		Scope.InnerType type;

		if (!lco.isVar()) {
			co.code.addAll(lco.code);

			if (lco.type.type == Scope.InnerType.INT) {
				load = new Lw(generateTemp(Scope.InnerType.INT), lco.temp, "0");
			} else if (lco.type.type == Scope.InnerType.FLOAT) {
				load = new Flw(generateTemp(Scope.InnerType.FLOAT), lco.temp, "0");
			} else {
				type = lco.type.type;
				while (type == Scope.InnerType.PTR) {
					type = lco.type.getWrappedType().type;
					if (type == Scope.InnerType.INT) {
						load = new Lw(generateTemp(Scope.InnerType.INT), lco.temp, "0");
					} else if (type == Scope.InnerType.FLOAT) {
						load = new Flw(generateTemp(Scope.InnerType.FLOAT), lco.temp, "0");
					}
				}
			}
			co.code.add(load);
			co.temp = load.getDest();
			co.type = lco.getType();
			co.lval = false;
			return co;
		}

		SymbolTableEntry symbol = lco.getSTE();
		String address = symbol.addressToString();
		InstructionList il = new InstructionList();
		String temp;

		if (symbol.isLocal()) {
			if (lco.getType().type == Scope.InnerType.INT) {
				load = new Lw(generateTemp(Scope.InnerType.INT), "fp", address);
			} else if (lco.getType().type == Scope.InnerType.FLOAT) {
				load = new Flw(generateTemp(Scope.InnerType.FLOAT), "fp", address);
			} else if (lco.getType().type == Scope.InnerType.PTR) {
				load = new Lw(generateTemp(Scope.InnerType.INT), "fp", address);
			}
		} else {
			il = generateAddrFromVariable(lco);
			co.code.addAll(il);
			temp = il.getLast().getDest();

			if (lco.getType().type == Scope.InnerType.INT) {
				load = new Lw(generateTemp(Scope.InnerType.INT), temp, "0");
			} else if (lco.getType().type == Scope.InnerType.FLOAT) {
				load = new Flw(generateTemp(Scope.InnerType.FLOAT), temp, "0");
			}
		}

		co.code.add(load);
		co.temp = load.getDest();
		co.lval = false;
		co.type = lco.getType();

		return co;
	}

	/**
	 * Generate an instruction sequence that holds the address of the variable in a code object
	 * 
	 * If it's a global variable, just get the address from the symbol table
	 * 
	 * If it's a local variable, compute the address relative to the frame pointer (fp)
	 * 
	 * @param lco The code object holding a variable
	 * @return a list of instructions that puts the address of the variable in a register
	 */
	private InstructionList generateAddrFromVariable(CodeObject lco) {

		InstructionList il = new InstructionList();

		//Step 1:
		SymbolTableEntry symbol = lco.getSTE();
		String address = symbol.addressToString();

		//Step 2:
		Instruction compAddr = null;
		if (symbol.isLocal()) {
			compAddr = new Addi("fp", address, generateTemp(Scope.InnerType.INT));
		} else {
			compAddr = new La(generateTemp(Scope.InnerType.INT), address);
		}
		il.add(compAddr); 
		return il;
	}
}
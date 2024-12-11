[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/eUheEmAq)

# Step 8: Optimization Extension


## Background for Step 8 (Final Project Step)

In step 8, we are asked to propose and implement an additional optimization or feature of our choice on top of the existing compiler. We have chosen **Constant Folding** as our optimization.

### Constant Folding (Step 8 Report)

**Introduction:**
Constant folding is a classical compiler optimization that detects and pre-computes constant expressions at compile time. Instead of generating code that computes something like `2 + 3` at runtime, the compiler replaces it directly with `5`. This reduces the number of runtime arithmetic operations, can shorten code, and potentially improve performance.

**Why Constant Folding?**
- **Performance Gain:** By evaluating constant expressions at compile time, the generated code avoids unnecessary arithmetic instructions.
- **Reduced Code Size:** Immediate values are used instead of multiple instructions, which can lead to fewer instructions executed.
- **Simplicity:** Constant folding is straightforward and well-understood, making it an ideal "extra" step to implement after completing the main required features.

**Approach:**
1. Create a `ConstantFoldingVisitor` that traverses the AST after parsing and before code generation.
2. Whenever a `BinaryOpNode` is encountered, if both operands are constants (`IntLitNode` or `FloatLitNode`), evaluate the expression at compile time.
3. Replace the `BinaryOpNode` with a single `IntLitNode` or `FloatLitNode` containing the computed result.
4. If one operand is `int` and the other `float`, convert the int to float before folding, ensuring type rules are respected.

**Code Updates:**
- Add a `ConstantFoldingVisitor.java` in `ast.visitor` package.
- In `postprocess(BinaryOpNode, ...)`, implement logic to:
  - Check for `IntLitNode` or `FloatLitNode` operands.
  - Fold operations like `+`, `-`, `*`, `/` where safe (e.g., no division by zero).
  - Replace the node with a single literal node representing the folded result.
- In `Compiler.java`, after parsing (and building the AST) and before code generation, run the `ConstantFoldingVisitor`.
  
**Tests:**
Place `constfold_test.uC` and `constfold_test2.uC` in `tests` directory:
- `constfold_test.uC`: Tests simple constant folding, e.g. `a = 2 + 3;` becomes `a = 5;`, `b = (int)(4.0 * 2.0);` becomes `b = 8;` etc.
- `constfold_test2.uC`: Tests float folding, e.g. `x = 3.14 * 2.0;` folded to `x = 6.28;` and casting scenarios `(float)(5+5)` to `10.0`.

When running `testall`, these tests verify that constant folding took place by checking the output against a reference.

---

## Step 7: Original Project Options Recap (for completeness)

I selected **Option 2: Type conversions** for step 7. (If you chose a different option, indicate that here.)

---

---

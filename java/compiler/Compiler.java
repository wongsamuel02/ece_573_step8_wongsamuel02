package compiler;

import java.io.IOException;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import assembly.CodeGenerator;
import assembly.CodeObject;
import ast.ASTNode;
import ast.visitor.*;

import java.util.Collection;

public class Compiler {

	static public SymbolTable symbolTable = new SymbolTable();

	public Compiler() {
		
	}

	public static void main(String args[]) {
		
		assert(false);
		
		try {
			MicroCLexer lexer = new MicroCLexer(CharStreams.fromFileName(args[0]));
			
			MicroCParser parser = new MicroCParser(new CommonTokenStream(lexer));

			parser.setSymbolTable(symbolTable);
			
			parser.program(); //parse the program to build the ast
			
			//Print out the symbol table. Helpful for debugging
			symbolTable.printTable();

			ASTNode ast = parser.getAST();

			// Uncomment this line to print out your AST for debugging purposes
			PrintVisitor pv = new PrintVisitor();
			pv.run(ast);

			// After parsing and building the AST, before code generation:
			ConstantFoldingVisitor cfv = new ConstantFoldingVisitor();
			ast = cfv.run(ast);

			CodeGenerator cg = new CodeGenerator();
			CodeObject co = cg.run(ast);

			// Print out ".section .text"
			System.out.println(".section .text");

			// Print out the code. Runme script will redirect as necessary
			System.out.println(co);

			//Print out strings
			printStrings();

		} catch (IOException e) {
			System.out.println("File not found");
			e.printStackTrace();
			System.exit(1);
		}		
	}

	private static void printStrings() {
		System.out.println();
		System.out.println(".section .strings");

		Scope g = symbolTable.getGlobalScope();

		Collection<Scope.SymbolTableEntry> stes = g.getEntries();
		for (Scope.SymbolTableEntry ste : stes) {
			if (ste.getType().type == Scope.InnerType.STRING) {
				Scope.StringSymbolTableEntry sste = (Scope.StringSymbolTableEntry) ste;
				System.out.println(String.format("0x%x", sste.getAddress()) + " " + sste.getValue());
			}
		}
	}
	
}

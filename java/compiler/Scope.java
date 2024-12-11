package compiler;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Collection;

public abstract class Scope{

	static public enum InnerType {
		STRING,
		INT,
		FLOAT,
		PTR,
		INFER,
		VOID
	}

	static public class Type {

		public final InnerType type;

		public Type(InnerType type) {
			assert(type != InnerType.PTR);
			this.type = type;
			this.wrappedType = null;
		}

		private Type(InnerType type, Type wrappedType) {
			this.type = type;
			this.wrappedType = wrappedType;
		}

		public static Type pointerToType(Type wrappedType) {
			return new Type(InnerType.PTR, wrappedType);
		}

		final Type wrappedType;
		
		public Type getWrappedType() {
			return wrappedType;
		}

		@Override
		public String toString() {
			if (type != InnerType.PTR) {
				return type.toString();
			} else {
				return "PTR to " + wrappedType.toString();
			}
		}

		@Override
		public boolean equals(Object other) {
			if (other instanceof Type) {
				if (type != InnerType.PTR) {
					return this.type == ((Type) other).type;
				} else {
					return this.wrappedType.equals(((Type) other).getWrappedType());
				}
			} else {
				return false;
			}
		}
	}

	public enum ErrorType {
		SHADOW,
		ERROR,
		REDEC,
		NONE
	}
	
	protected LinkedHashMap<String, SymbolTableEntry> table;
	protected LinkedList<Scope> subScopes;
	protected String name;
	protected Scope parentTable;
	
	public Scope() {
		table = new LinkedHashMap<String, SymbolTableEntry>();
		subScopes = new LinkedList<Scope>();
		name = "";
		parentTable = null;
	}
	
	public Scope(Scope parent) {
		this();
		parentTable = parent;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public ErrorType addSymbol(Type type, String name) {
		ErrorType retVal = checkSymbol(name);
		table.put(name, genSymbol(type, name));
		return retVal;
	}

	abstract protected SymbolTableEntry genSymbol(Type type, String name);
	
	public ErrorType addSymbol(Type type, String name, String value) {
		ErrorType retVal = checkSymbol(name);
		table.put(name, genStringSymbol(type, name, value));
		return retVal;
	}

	abstract protected StringSymbolTableEntry genStringSymbol(Type type, String name, String value);
	
	public Scope addSubScope(String name) {
		Scope newScope = new LocalScope(this);
		newScope.setName(name);
		subScopes.add(newScope);
		return newScope;
	}
	
	public SymbolTableEntry getSymbolTableEntry(String name) {
		SymbolTableEntry retval = null;

		retval = searchLocalScope(name);
		if (retval != null) {
			return retval;
		} else {
			if (parentTable == null) return null;
			else return parentTable.getSymbolTableEntry(name);
		}
	}
	
	protected abstract SymbolTableEntry searchLocalScope(String name);

	protected ErrorType checkSymbol(String name) {
		SymbolTableEntry ste = searchLocalScope(name);
		if (ste != null) { //if the symbol is already in the local scope
			//if the symbol is a function scope, and it has not been defined yet, return REDEC
			if (ste instanceof FunctionSymbolTableEntry) {
				FunctionSymbolTableEntry fste = (FunctionSymbolTableEntry) ste;
				if (!fste.isDefined()) {
					return ErrorType.REDEC;
				}
			}
			return ErrorType.ERROR;
		} else { //no error at current scope
			if (parentTable == null) return ErrorType.NONE;
			if (parentTable.checkSymbol(name) != ErrorType.NONE) {
				return ErrorType.SHADOW;
			}
		}
		return ErrorType.NONE;
	}
	
	public void printTable() {
		printLocalTable();
		for (Scope st : subScopes) {
			st.printTable();
		}
	}
	
	protected void printLocalTable() {
		System.out.println("; Symbol table " + name);
		
		for (SymbolTableEntry ste : table.values())
			System.out.println(ste);
			
		System.out.println();
	}
	
	static public class SymbolTableEntry {

		private String name;
		private Scope.Type type;
		private int address;
		private boolean isLocal;
		
		public SymbolTableEntry(Scope.Type type, String name, int address, boolean isLocal) {
			this.setName(name);
			this.setType(type);
			this.setAddress(address);
			this.isLocal = isLocal;
		}

		public SymbolTableEntry(Scope.Type type, String name, int address) {
			this(type, name, address, false);
		}
		
		public String toString() {
			//print the hex address if it's global, print the decimal offset if it's local
			return "; name " + this.getName() + " type " + getType() + " location " + addressToString();
		}

		public Scope.Type getType() {
			return type;
		}

		public void setType(Scope.Type type) {
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAddress() {
			return address;
		}

		public void setAddress(int address) {
			this.address = address;
		}

		public String addressToString() {
			return ((isLocal) ? String.valueOf(getAddress()) : String.format("0x%x", getAddress()));
		}

		public boolean isLocal() {
			return this.isLocal;
		}
		
	}
	
	static public class StringSymbolTableEntry extends SymbolTableEntry {
		
		private String value;
		
		public StringSymbolTableEntry(Scope.Type type, String name, String value, int address, boolean isLocal) {
			super(type, name, address, isLocal);
			this.setValue(value);
		}

		public StringSymbolTableEntry(Scope.Type type, String name, String value, int address) {
			this(type, name, value, address, false);
		}
		
		public String toString() {
			return super.toString() + " value " + getValue();
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	static public class FunctionSymbolTableEntry extends SymbolTableEntry {
		
		private List<Type> argTypes;
		private boolean isDefined;

		public FunctionSymbolTableEntry(Scope.Type returnType, String name, List<Type> argTypes) {
			super(returnType, name, 0);
			this.argTypes = argTypes;
			this.isDefined = false;
		}

		public List<Type> getArgTypes() {
			return argTypes;
		}

		public Type getReturnType() {
			return this.getType();
		}

		public String toString() {
			return "; Function: " + getReturnType() + " " + getName() + "(" + getArgTypes() + ")";
		}

		public boolean isDefined() {
			return isDefined;
		}

		public void setDefined(boolean isDefined) {
			this.isDefined = isDefined;
		}
		
	}

	public Collection<SymbolTableEntry> getEntries() {
		return table.values();
	}
	
}
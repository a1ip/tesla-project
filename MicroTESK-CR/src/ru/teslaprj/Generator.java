package ru.teslaprj;

import java.math.BigInteger;
import java.util.Map;

public class Generator
{
	private Solver solver = new Solver();
	private TextConstructor text_constructor = null;
	private Microprocessor microprocessor = null;

	public Program generate( Template template, boolean with_binsearch )
		throws Unsat, Timeout
	{
		if ( microprocessor != null )
			solver.setMicroprocessor( microprocessor );
		
		if (! is_initialized() )
			throw new IllegalStateException("Generator isn't initialized");
		
		Map<Parameter, BigInteger> solution = solver.solve(template, with_binsearch );
		if ( solution.isEmpty() )
			return text_constructor.createEmptyProgram();
		else
			return text_constructor.build_initialization( solution, solver.getInitLengths(), template );
	}

	
	private boolean is_initialized()
	{
		return is_constructor_initialized()
			&& is_solver_initialized();
	}


	private boolean is_solver_initialized() {
		return solver.is_initialized();
	}


	private boolean is_constructor_initialized() {
		return text_constructor != null;
	}


	/**
	 * @param text_constructor the text_constructor to set
	 */
	public void setTextConstructor(TextConstructor text_constructor) {
		this.text_constructor = text_constructor;
	}
	
	public void setMicroprocessor( Microprocessor microprocessor )
	{
		this.microprocessor = microprocessor;
	}
	
	public void setPathToRubySources( String path )
	{
		solver.setPathToRubySources( path );
	}
	
	public void setJrubyHome( String path )
	{
		solver.setJrubyHome( path );
	}

	public void setJrubyLib( String path )
	{
		solver.setJrubyLib( path );
	}

	public void setJrubyShell( String path )
	{
		solver.setJrubyShell( path );
	}

	public void setJrubyScript( String path )
	{
		solver.setJrubyScript( path );
	}
	
	public void setSolver( Solver solver )
	{
		this.solver = solver;
	}
}

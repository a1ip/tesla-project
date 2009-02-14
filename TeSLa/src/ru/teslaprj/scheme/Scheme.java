package ru.teslaprj.scheme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Scheme
{
	private List<Command> commands = new ArrayList<Command>();
	private List<Definition> defs = new ArrayList<Definition>();
	private Set<Assert> asserts = new HashSet<Assert>();
	
	public void addCommand( Command command )
	{
		commands.add( command );
	}
	
	public void addDefinition( Definition def )
		throws CommandDefinitionError
	{
		if ( def instanceof NameBitlenDefinition 
				&& alreadyDefined( ((NameBitlenDefinition)def).getName() ) )
			throw new CommandDefinitionError( "name is already defined: '" + ((NameBitlenDefinition)def).getName() + "'" );
		
		defs.add( def );
	}
	
	public void addAssert( Assert assert1 )
	{
		asserts.add( assert1 );
	}
	
	public String toString()
	{
		//TODO pretty print of scheme
		return "sorry, not implemented yet";
	}
	
	private boolean alreadyDefined( String name )
	{
		for( Definition def : defs )
		{
			if ( def instanceof NameBitlenDefinition
					&& ((NameBitlenDefinition)def).getName().equals( name ) )
				return true;
		}
		
		return false;
	}
	
	public List<String> getDefinedNames()
	{
		List<String> names = new ArrayList<String>();
		
		for( Definition def : defs )
		{
			if ( def instanceof NameBitlenDefinition )
			{
				names.add( ( (NameBitlenDefinition)def ).getName() );
			}
		}
		
		return names;
	}
	
	public List<Command> getCommands()
	{
		return commands;
	}
	
	public List<Definition> getDefinitions()
	{
		return defs;
	}
	
	public Set<Assert> getAsserts()
	{
		return asserts;
	}
	
	public int getBitlen( String varName )
	{
		for( Definition def : defs )
		{
			if( def instanceof NameBitlenDefinition )
			{
				String name = ((NameBitlenDefinition)def).getName();
				if ( name.equals( varName ) )
					return ((NameBitlenDefinition)def).getBitlen();
			}
		}
		return -1;
	}
}

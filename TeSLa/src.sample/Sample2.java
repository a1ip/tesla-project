import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import ru.teslaprj.Cache;
import ru.teslaprj.Solver;
import ru.teslaprj.scheme.Command;
import ru.teslaprj.scheme.RegisterDefinition;
import ru.teslaprj.scheme.Scheme;

public class Sample2
{
	public static void main( String[] args )
	{
		try
		{
			// 1. ������������ �����
			Scheme scheme = new Scheme() ;
			scheme.addDefinition( new RegisterDefinition( "x", 64 ) );
			for( int i = 0; i < 4; i++ )
			{
				//WORD_VALUE = 10 => max = 2
				//WORD_VALUE = 12 => max = 2
				//WORD_VALUE = 14 => max = 6
				//WORD_VALUE = 16 => max = 14  ( - 6 )
				//WORD_VALUE = 18 => max = 8
				//WORD_VALUE = 20 => max = 2
				//WORD_VALUE = 22 => max = 18  ( - 4 )
				//WORD_VALUE = 24 => max = 14
				//WORD_VALUE = 26 => max = 10
				//WORD_VALUE = 28 => max = 6
				//WORD_VALUE = 30 => max = 2
				//WORD_VALUE = 31 => max = 0
				//WORD_VALUE = 32 => max = 30   ( - 2 )
				//WORD_VALUE = 33 => max = 29
				//WORD_VALUE = 38 => max = 24
				//WORD_VALUE = 39 => max = 23
				//WORD_VALUE = 40 => max = 22
				//WORD_VALUE = 50 => max = 12
				scheme.addCommand( 
						new Command(
								  scheme
								, "ADD"
								, Arrays.asList( "x", "x", "x" )
								, "nover"
							) );
			}
			scheme.addCommand( 
					new Command(
							  scheme
							, "ADD"
							, Arrays.asList( "x", "x", "x" )
							, "over"
						) );
					
			// 2. ������� ��������
			Solver solver = new Solver( 
					new File("src.sample" )
					, new File("clp")
				);
			//Verdict verdict = 
				solver.solve(scheme, new ArrayList<Cache>(), null );
			
//			// 3. ����������� �����
//			Map<Definition, BigInteger> values = verdict.getDefinitionValues();
//			for( Definition def : values.keySet() )
//			{
//				System.out.println( def + " = " + values.get( def ) );	
//			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
	}
}
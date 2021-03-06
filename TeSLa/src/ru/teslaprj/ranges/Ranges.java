package ru.teslaprj.ranges;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.teslaprj.Cache;
import ru.teslaprj.TLB;
import ru.teslaprj.TLBRow;
import ru.teslaprj.ranges.ts.EvictingTlbHit;
import ru.teslaprj.ranges.ts.InitialTlbHit;
import ru.teslaprj.ranges.ts.InitialTlbMiss;
import ru.teslaprj.ranges.ts.UnusefulTlbMiss;
import ru.teslaprj.ranges.ts.UsefulTlbMiss;
import ru.teslaprj.scheme.Command;
import ru.teslaprj.scheme.MemoryCommand;
import ru.teslaprj.scheme.Scheme;
import yices.YicesLite;

public class Ranges
{
	final Map<MemoryCommand, L1Range> l1Ranges;
	final Map<MemoryCommand, TLBRange> tlbRanges;
	YicesLite yl;
	int context;
	
	final int tagsetLength;
	final int pfnLength;
	final int set1Length;
	final int virtualAddressLength;
	final int cacheOffsetLength;
	
	final int tlbAssoc;
	final Cache dataL1;
	final TLB dtlb;
	final int vpnLength;
	
	public Ranges(L1Range[] l1values, TLBRange[] tlbvalues, Cache dataL1, TLB tlb)
	{
		l1Ranges = new HashMap<MemoryCommand, L1Range>();
		for( L1Range r : l1values )
		{
			l1Ranges.put(r.getCommand(), r);
			r.setContext(this);
		}
		tlbRanges = new HashMap<MemoryCommand, TLBRange>();
		for( TLBRange r : tlbvalues )
		{
			tlbRanges.put(r.getCommand(), r);
			r.setContext(this);
		}
		tlbAssoc = tlb.getDTLBSize();
		
		tagsetLength = dataL1.getTagBitLength() + dataL1.getSetNumberBitLength();
		pfnLength = tlb.getPFNBitLen();
		set1Length = dataL1.getSetNumberBitLength();
		virtualAddressLength = 64;
		cacheOffsetLength = tlb.getPABITS() - tagsetLength;
		vpnLength = tlb.getSEGBITS() - (tlb.getPABITS() - pfnLength);
		
		this.dataL1 = dataL1;
		this.dtlb = tlb;
	}

	static int nnn = 0;
	public Map<String, Long> isConsistency0() throws Inconsistent
	{
		yl = new YicesLite();
		yl.yicesl_enable_log_file("system" + (++nnn) + ".txt" );
		System.out.print("system" + nnn + ": ");
		yl.yicesl_set_verbosity((short)0);
		yl.yicesl_enable_type_checker((short)1);
		yl.yicesl_set_output_file("system-output" + nnn + ".txt");
		context = yl.yicesl_mk_context();
		
		try
		{
			yl.yicesl_read(context, "(set-evidence! true)");
			
			yl.yicesl_read( context, "(define-type tagset (bitvector " + tagsetLength + "))" );
			yl.yicesl_read( context, "(define-type pfn (bitvector " + pfnLength + "))" );
			yl.yicesl_read( context, "(define-type set1 (bitvector " + set1Length + "))" );
			yl.yicesl_read( context, "(define-type virtualAddress (bitvector " + virtualAddressLength + "))" );
			yl.yicesl_read( context, "(define-type cacheOffset (bitvector " + cacheOffsetLength + "))" );
			yl.yicesl_read( context, "(define-type vpn (bitvector " + vpnLength + "))" );
			
			yl.yicesl_read( context, "(define getPfn :: (-> tagset pfn) " +
					"(lambda " +
						"(x :: tagset) " +
						"(bv-extract " + (tagsetLength-1) + " " + (tagsetLength-pfnLength) + " x)" +
					"))" );
			yl.yicesl_read( context, "(define getRegion1 :: (-> tagset set1) " +
					"(lambda " +
						"(x :: tagset) " +
						"(bv-extract " + (set1Length-1) + " 0 x)" +
					"))" );
			yl.yicesl_read( context, "(define getCacheOffset :: (-> virtualAddress cacheOffset) " +
					"(lambda " +
						"(x :: virtualAddress) " +
						"(bv-extract " + (cacheOffsetLength-1) + " 0 x)" +
					"))" );
			yl.yicesl_read( context, "(define getVPN :: (-> virtualAddress vpn) " +
					"(lambda " +
						"(x :: virtualAddress) " +
						"(bv-extract " + (dtlb.getSEGBITS() - 1) + " " + (dtlb.getPABITS() - pfnLength) + " x)" +
					"))" );
			yl.yicesl_read( context, "(define pfntype :: (-> tagset int) " +
					"(lambda " +
						"(x :: tagset) 0))");
			
			for( MemoryCommand cmd : l1Ranges.keySet() )
			{
				yl.yicesl_read( context, "(define " + cmd.getTagset() + " :: tagset)" );
			}
			
	        Scheme scheme = null;
	        for(MemoryCommand cmd : l1Ranges.keySet() )
	        {
	        	scheme = cmd.getScheme();
	        	break;
	        }
	        List<MemoryCommand> commands = new ArrayList<MemoryCommand>();
			for( Command cmd : scheme.getCommands() )
			{
				if ( l1Ranges.containsKey(cmd) && tlbRanges.containsKey(cmd) )
				{
					// `Cached Mapped' case
					tlbRanges.get(cmd).visit( l1Ranges.get(cmd) );
					commands.add((MemoryCommand)cmd);
				}
				//TODO else - ��������� ������
			}
			
//TODO ��� ����� ���������� ������ consistent!	yl.yicesl_read(context, "(check)");
			
			postDefine("x0", "(bitvector 64)", "");
			postDefine("x1", "(bitvector 64)", "");
			postDefine("x2", "(bitvector 64)", "");
			postDefine("y0", "(bitvector 64)", "");
			postDefine("y1", "(bitvector 64)", "");
			postDefine("y2", "(bitvector 64)", "");
			postDefine("s0", "(bitvector 64)", "");
			postDefine("s1", "(bitvector 64)", "");
			postDefine("s2", "(bitvector 64)", "");
			postDefine("t0", "(bitvector 64)", "");
			postDefine("t1", "(bitvector 64)", "");
			postDefine("t2", "(bitvector 64)", "");
			postDefine("cc", "(bitvector 64)", "");
			postDefine("ccc", "(bitvector 64)", "");
			postDefine("cccc", "(bitvector 64)", "");
			postDefine("ccccc", "(bitvector 64)", "");
			postDefine("cccccc", "(bitvector 64)", "");
			for( MemoryCommand cmd : commands )
			{
				postDefine( 
						cmd.getVirtualAddress(), 
						"virtualAddress", 
						"(bv-add " + cmd.getArgs().get(1) + "0 " + cmd.getArgs().get(2) + ")"
					);
			}
			

			
			//TODO ������� ������ �������
			//��� ���� ������� ������ ��� ���� ����������
//			if ( yl.yicesl_inconsistent(context) == 0)
				loadstore2Constraints0(commands);
			
			// ����������� �� ������� ����� ������ TLB � ������������ ������
			// (����������� �� ����� ����������� �������� �� pfn)
//			if ( yl.yicesl_inconsistent(context) == 0)
				for( MemoryCommand cmd : commands )
					virtualAddresses2TLBlinesConstraints(cmd);

			// ����������� ������������ ������, �������� ��� �������:
//			if ( yl.yicesl_inconsistent(context) == 0)	
			for( MemoryCommand cmd : commands )
			{
				//TODO this only for `Mapped' case and SEGBITS=40, PABITS=36
				postAssert( new StringBuffer( "(= (bv-extract 63 30 ")
					.append( cmd.getVirtualAddress() ).append(") (mk-bv 34 17179869183))")
						.toString() );
			}
			
			// ����� ������� -- ��� ����� ������������ ������
//			if ( yl.yicesl_inconsistent(context) == 0 )
				for( MemoryCommand cmd : commands )
				{
					postAssert( new StringBuffer( "(= (bv-extract " ).append( tagsetLength - pfnLength - 1)
							.append( " 0 " ).append( cmd.getTagset() ).append(") (bv-extract ")
							.append( dtlb.getPABITS() - pfnLength - 1 ).append(" " ).append( dtlb.getPABITS() - tagsetLength )
							.append(" " ).append( cmd.getVirtualAddress() ).append("))" ).toString() );
				}
			
			yl.yicesl_read(context, "(check)");// ��� ����� �� ���������� ������...
			float t1 = System.currentTimeMillis();
			boolean consistent = (yl.yicesl_inconsistent(context) == 0);
			float t2 = System.currentTimeMillis();
			
	
//	        try
//	        {
//		        BufferedWriter w = new BufferedWriter( 
//		        		new FileWriter( new File( "system" + nnn + ".txt" ), true ) );
//		        for( Command cmd : scheme.getCommands() )
//		        {
//		        	if ( l1Ranges.containsKey(cmd) && tlbRanges.containsKey(cmd) )
//		        	{
//		        		w.write("[ " + l1Ranges.get(cmd).print() + " || " +
//		        				tlbRanges.get(cmd).print() + " ]" );
//		        		w.newLine();
//		        	}
//		        }
//		        
//		        w.close();
//	        }
//	        catch( IOException e )
//	        {
//	        	System.out.println(e.getStackTrace());
//	        }
	        
	        if ( consistent )
	        {
	        	try
	        	{
	        		BufferedReader r = new BufferedReader( new FileReader( new File("system-output" + nnn + ".txt")));
	        		String line = r.readLine(); System.out.println("model:");
	    			Map<String, Long> result = new HashMap<String, Long>();
	        		while( (line = r.readLine()) != null )
	        		{
	        			System.out.println(line);
	        			String[] parts = line.split(" ");
	        			if ( parts.length < 3 )
	        				continue;
	        			int i = 0;
	        			while( i < parts.length )
	        			{
	        				i++;
		        			String name = parts[i++];
		        			if ( ! name.endsWith("1") && ! name.endsWith("2") || name.startsWith("ts") )
		        			{
			        			if ( name.endsWith("0") )
			        				name = name.substring(0, name.length() - 1 );
			        			String bitvalue = parts[i].substring(2, parts[i].length() - 1 );
			        			BigInteger v = new BigInteger(bitvalue, 2);
			        			System.out.println(name + " = " + v.longValue());
			        			result.put(name, v.longValue());
		        			}
		        			i++;
	        			}
	        		}
	        		
	        		System.out.println("t1 = " + t1);
	        		System.out.println("t2 = " + t2);
	        		System.out.println("TIME: " + (t2 - t1)/1000.0 + "s");
	        		System.gc();
	        		
	        		return result;
	        	}
	        	catch(FileNotFoundException e ){}
	        	catch(IOException e){}
	        }
	        
	        return null;
		}
		finally
		{
	        yl.yicesl_del_context(context);
		}
	}

	public Map<String, Long> isConsistency1() throws Inconsistent
	{
		yl = new YicesLite();
		yl.yicesl_enable_log_file("system-1-" + (++nnn) + ".txt" );
		System.out.print("system" + nnn + ": ");
		yl.yicesl_set_verbosity((short)0);
		yl.yicesl_enable_type_checker((short)1);
		yl.yicesl_set_output_file("system-1-output" + nnn + ".txt");
		context = yl.yicesl_mk_context();
		
		try
		{
			yl.yicesl_read( context, "(set-evidence! true)" );
			
			yl.yicesl_read( context, "(define-type Tagset)" );
			yl.yicesl_read( context, "(define-type tagset (bitvector " + tagsetLength + "))" );
			yl.yicesl_read( context, "(define-type pfn (bitvector " + pfnLength + "))" );
			yl.yicesl_read( context, "(define-type set1 (bitvector " + set1Length + "))" );
			yl.yicesl_read( context, "(define-type virtualAddress (bitvector " + virtualAddressLength + "))" );
			yl.yicesl_read( context, "(define-type cacheOffset (bitvector " + cacheOffsetLength + "))" );
			yl.yicesl_read( context, "(define-type vpn (bitvector " + vpnLength + "))" );

			yl.yicesl_read( context, "(define pfntype::(-> Tagset (subrange 0 3)))" );
			yl.yicesl_read( context, "(define pfneq::(-> Tagset Tagset bool))" );
			yl.yicesl_read( context, "(define regioneq::(-> Tagset Tagset bool))" );
			yl.yicesl_read( context, "(define section::(-> Tagset int bool))" );
			yl.yicesl_read( context, "(define among-section::(-> Tagset int int bool))" );
						
			yl.yicesl_read( context, "(define getPfn :: (-> tagset pfn) " +
					"(lambda " +
						"(x :: tagset) " +
						"(bv-extract " + (tagsetLength-1) + " " + (tagsetLength-pfnLength) + " x)" +
					"))" );
			yl.yicesl_read( context, "(define getRegion1 :: (-> tagset set1) " +
					"(lambda " +
						"(x :: tagset) " +
						"(bv-extract " + (set1Length-1) + " 0 x)" +
					"))" );
			yl.yicesl_read( context, "(define getCacheOffset :: (-> virtualAddress cacheOffset) " +
					"(lambda " +
						"(x :: virtualAddress) " +
						"(bv-extract " + (cacheOffsetLength-1) + " 0 x)" +
					"))" );
			yl.yicesl_read( context, "(define getVPN :: (-> virtualAddress vpn) " +
					"(lambda " +
						"(x :: virtualAddress) " +
						"(bv-extract " + (dtlb.getSEGBITS() - 1) + " " + (dtlb.getPABITS() - pfnLength) + " x)" +
					"))" );
			if ( tagsetLength > pfnLength )
				yl.yicesl_read( context, "(define rangeeq :: (-> int int Tagset (bitvector "
						+ (tagsetLength - pfnLength) + ") bool))");
			
			//axioms
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(=> (and (= x y)(> (pfntype x) 1)) (pfneq x y))))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(=> (and (= x y)(> (pfntype x) 1)) (regioneq x y))))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(=> (and (pfneq x y)(> (pfntype x) 1)(regioneq x y)) (= x y))))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(=> (pfneq x y) (or (and (< (pfntype x) 2) (= (pfntype x) (pfntype y))) (and (> (pfntype x) 1)(> (pfntype y) 1))))))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(=> (and (pfneq x y) (> (pfntype x) 1)) (regioneq x y))))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset)(= (regioneq x y) (regioneq y x))))" );
//			yl.yicesl_read( context, "(assert (forall (x::Tagset y::Tagset v::(bitvector 7) end::int start::int)" +
//			"(=> (and (= x y)  (rangeeq end start x v)) (rangeeq end start y v))  ))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset v1::(bitvector 7) v2::(bitvector 7) end::int start::int)" +
			"(=> (and (rangeeq end start x v1)  (rangeeq end start x v2)) (= v1 v2))  ))" );
			yl.yicesl_read( context, "(assert (forall (x::Tagset s::int e::int m::int) " +
					"(= (and (section x m) (among-section x s e)) (and (<= s m) (<= m e)))))" );
///////		addition axiom: if type(ts1)=type(ts2) and (ts1:0 \/ ts1:1) -> (value_ts(ts1) = value_ts(ts2) <=> ts1 = ts2)

			
	        Scheme scheme = null;
	        for(MemoryCommand cmd : l1Ranges.keySet() )
	        {
	        	scheme = cmd.getScheme();
	        	break;
	        }
	        List<MemoryCommand> commands = new ArrayList<MemoryCommand>();
			for( Command cmd : scheme.getCommands() )
			{
				if ( l1Ranges.containsKey(cmd) && tlbRanges.containsKey(cmd) )
				{
					// `Cached Mapped' case
					tlbRanges.get(cmd).visit1( l1Ranges.get(cmd) );
					commands.add((MemoryCommand)cmd);
				}
				//TODO else - ��������� ������
			}
			
//			// ������ ������, �� ��� ������ ���������� �� ���������; ������, ��-�� ��� ������
//			// (forall (x::Tagset y::Tagset)  (=> (= x y) (pfneq x y)))   )
//			Set<MemoryCommand> viewed = new HashSet<MemoryCommand>();
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (= " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							" (pfneq " + c1.getTagset() + " " + c2.getTagset() +"))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
//			// (forall (x::Tagset y::Tagset)   (=> (= x y) (regioneq x y))  )
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (= " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							" (regioneq " + c1.getTagset() + " " + c2.getTagset() +"))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
//			// (forall (x::Tagset y::Tagset)  (=> (and (pfneq x y) (regioneq x y)) (= x y))   )
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (and (pfneq " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							"(regioneq " + c1.getTagset() + " " + c2.getTagset() + "))" +
//							" (= " + c1.getTagset() + " " + c2.getTagset() +"))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
//			// (forall (x::Tagset y::Tagset)   (=> (pfneq x y) (= (pfntype x) (pfntype y) ))   )
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (pfneq " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							" (= (pfntype " + c1.getTagset() + ") (pfntype " + c2.getTagset() +")))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
//			// (forall (x::Tagset y::Tagset)   (=> (pfneq x y) (regioneq x y))    )
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (pfneq " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							" (regioneq " + c1.getTagset() + " " + c2.getTagset() +"))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
//			// (forall (x::Tagset y::Tagset)   (= (regioneq x y) (regioneq y x))   )
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(= (regioneq " + c1.getTagset() + " " + c2.getTagset() + ")" +
//							" (regioneq " + c2.getTagset() + " " + c1.getTagset() +"))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
//			
////			addition axiom: if type(ts1)=type(ts2) and (ts1:0 \/ ts1:1) -> (value_ts(ts1) = value_ts(ts2) <=> ts1 = ts2)
//			for( MemoryCommand c1 : commands )
//			{
//				for( MemoryCommand c2 : commands )
//				{
//					if ( c2 == c1 )
//						continue;
//					if ( viewed.contains(c2) )
//						continue;
//					postAssert("(=> (and (= (pfntype " + c1.getTagset() + ") (pfntype " + c2.getTagset() + "))" + 
//							"(< (pfntype " + c1.getTagset() + ") 2))" +
//							"(= (= " + c1.getTagset() + " " + c2.getTagset() + ") " +
//							"(= " + c1.getValueOfTagset() + " " + c2.getValueOfTagset() +")))");
//				}
//				viewed.add(c1);
//			}
//			viewed.clear();
			
			
//TODO ��� ����� ���������� ������ consistent!	yl.yicesl_read(context, "(check)");
			
			postDefine("x0", "(bitvector 64)", "");
			postDefine("x1", "(bitvector 64)", "");
			postDefine("x2", "(bitvector 64)", "");
			postDefine("x3", "(bitvector 64)", "");
			postDefine("x4", "(bitvector 64)", "");
			postDefine("y0", "(bitvector 64)", "");
			postDefine("cc", "(bitvector 64)", "");
			postDefine("ccc", "(bitvector 64)", "");
			postDefine("cccc", "(bitvector 64)", "");
			postDefine("ccccc", "(bitvector 64)", "");
			postDefine("cccccc", "(bitvector 64)", "");
			for( MemoryCommand cmd : commands )
			{
				postDefine( 
						cmd.getVirtualAddress(), 
						"virtualAddress", 
						"(bv-add " + cmd.getArgs().get(1) + "0 " + cmd.getArgs().get(2) + ")"
					);
			}
			

			
			//TODO ������� ������ �������
			//��� ���� ������� ������ ��� ���� ����������
			if ( yl.yicesl_inconsistent(context) == 0)
			{
				MemoryCommand prevCommand = null;
				for( MemoryCommand cmd : commands )
				{
					if ( prevCommand != null )
						loadstore2Constraints1(prevCommand, cmd);
					prevCommand = cmd;
				}
			}
			
			// ����������� �� ������� ����� ������ TLB � ������������ ������
			// (����������� �� ����� ����������� �������� �� pfn)
			if ( yl.yicesl_inconsistent(context) == 0)
				for( MemoryCommand cmd : commands )
				{
					virtualAddresses2TLBlinesConstraints(cmd);
				}

			// ����������� ������������ ������, �������� ��� �������:
			if ( yl.yicesl_inconsistent(context) == 0)	
			for( MemoryCommand cmd : commands )
			{
				//TODO this only for `Mapped' case and SEGBITS=40, PABITS=36
				postAssert( new StringBuffer( "(= (bv-extract 63 30 ")
					.append( cmd.getVirtualAddress() ).append(") (mk-bv 34 17179869183))")
						.toString() );
			}
			
			// ����� ������� -- ��� ����� ������������ ������
			if ( yl.yicesl_inconsistent(context) == 0 )
			if ( tagsetLength > pfnLength )
				for( MemoryCommand cmd : commands )
				{
					postAssert( new StringBuffer( "(ite (< (pfntype " )
							.append( cmd.getTagset()).append(") 2) (= (bv-extract " )
							.append( tagsetLength - pfnLength - 1)
							.append( " 0 " ).append( cmd.getValueOfTagset() ).append(") (bv-extract ")
							.append( dtlb.getPABITS() - pfnLength - 1 ).append(" " )
							.append( dtlb.getPABITS() - tagsetLength )
							.append(" " ).append( cmd.getVirtualAddress() ).append(")) (rangeeq " )
							.append( tagsetLength - pfnLength - 1)
							.append( " 0 " ).append( cmd.getTagset() ).append(" (bv-extract ")
							.append( dtlb.getPABITS() - pfnLength - 1 ).append(" " ).append( dtlb.getPABITS() - tagsetLength )
							.append(" " ).append( cmd.getVirtualAddress() ).append("))  )").toString() );
					//TODO �������� ����������, ��� ���� ����� ������� ��� external
					// ������ ����������� ����� �������� ����� ����� � ^L^
					// ���� ����� �������� ����, �������� �� ��� ts[..] \in {...}
					// ���� ����� �������� �����, �� �� ����� �������� ����,
					//		�� �������� �� ��� ts[..] \notin {...}
					// ���� � �� �����, � �� �� �����, �� �������� ��� ����
					// 		- �� ���� ������ ����� ����� ���������������
					// TODO �������� �� ��� ����������� ����� ����������� ��
					// �������� ��������; ��������, ���� �������� �������
					// ������� ����� ������ � ^L^, �� ����� ��� ts[..]
					// �������� ������ �� ������ ������
				}
			
			//TODO �����������, ��� external ������� �� ����� ���� �����
			// M ��� PFN\M
			
			yl.yicesl_read(context, "(check)");// ��� ����� �� ���������� ������...
			int ttt = yl.yicesl_inconsistent(context);
			boolean consistent = (ttt == 0);
	
//	        try
//	        {
//		        BufferedWriter w = new BufferedWriter( 
//		        		new FileWriter( new File( "system-1-" + nnn + ".txt" ), true ) );
//		        for( Command cmd : scheme.getCommands() )
//		        {
//		        	if ( l1Ranges.containsKey(cmd) && tlbRanges.containsKey(cmd) )
//		        	{
//		        		w.write("[ " + l1Ranges.get(cmd).print() + " || " +
//		        				tlbRanges.get(cmd).print() + " ]" );
//		        		w.newLine();
//		        	}
//		        }
//		        
//		        w.close();
//	        }
//	        catch( IOException e )
//	        {
//	        	System.out.println(e.getStackTrace());
//	        }
	        
	        if ( consistent )
	        {
	        	try
	        	{
	        		BufferedReader r = new BufferedReader( new FileReader( new File("system-1-output" + nnn + ".txt")));
	        		String line = r.readLine(); System.out.println("model:");
	    			Map<String, Long> result = new HashMap<String, Long>();
	        		while( (line = r.readLine()) != null )
	        		{
	        			System.out.println(line);
//	        			String[] parts = line.split(" ");
//	        			if ( parts.length < 3 )
//	        				continue;
//	        			int i = 0;
//	        			while( i < parts.length )
//	        			{
//	        				i++;
//		        			String name = parts[i++];
//		        			if ( ! name.endsWith("1") && ! name.endsWith("2") || name.startsWith("ts") )
//		        			{
//			        			if ( name.endsWith("0") )
//			        				name = name.substring(0, name.length() - 1 );
//			        			String bitvalue = parts[i].substring(2, parts[i].length() - 1 );
//			        			BigInteger v = new BigInteger(bitvalue, 2);
//			        			System.out.println(name + " = " + v.longValue());
//			        			result.put(name, v.longValue());
//		        			}
//		        			i++;
//	        			}
	        		}
	        		return result;
	        	}
	        	catch(FileNotFoundException e ){}
	        	catch(IOException e){}
	        }
	        
	        return null;
		}
		finally
		{
	        yl.yicesl_del_context(context);
		}
	}

	private void virtualAddresses2TLBlinesConstraints(MemoryCommand cmd)
	{
//		for( MemoryCommand cmd : commands )
		{
			//cmd has pfn
			//cmd has virtualAddress
			//virtualAddress conforms to the line with this pfn
			// + oddbit constraint!
			
			// ������� ������ TLB c ������ �������� ��������
			// ����� ��� ���� pfn �����-��, �� ����� ������������ ������ �����-��(c ����� ��������!)
			if ( tlbRanges.get(cmd) instanceof InitialTlbHit )
			{
				// ���� ������ ����� M
				StringBuffer constraint = new StringBuffer("(=> (< (pfntype ")
				.append( cmd.getTagset() ).append(") 2) (or false ");
				for( int p : dtlb.getDTLB() )
				{
					TLBRow r = dtlb.getRow(p);
					// TODO ����� ������ � ������ ��������
					if ( dtlb.getPABITS() - pfnLength + 1 >= 30 )
					{
						if ( r.getVPNd2().intValue() != (int)Math.pow(2, dtlb.getSEGBITS()
								- dtlb.getPABITS() + pfnLength) - 1 )
							continue;
					}
					else
					{
						//������� 10 ��� ������ ����������� �������� == 1023
						if ( r.getVPNd2().shiftRight(30 - dtlb.getPABITS() + pfnLength).intValue()
								!= 255 )
							continue;
					}
					if ( r.getMask() == pfnLength && r.getValid0() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN0().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 ).append(")))");
					}
					if ( r.getMask() == pfnLength && r.getValid1() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN1().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 + 1 ).append(")))");
					}
				}
				postAssert( constraint.append("))").toString() );
			}
			else if ( tlbRanges.get(cmd) instanceof EvictingTlbHit )
			{
				// ��������� ������ ����.�������, �.�. ��������� pfn'�
				Set<MemoryCommand> evictings = ((EvictingTlbHit)tlbRanges.get(cmd)).getEvictings();
				//(=> (= pfn1 pfn2) (= (vpn va1) (vpn va2)))
				StringBuffer constraint = new StringBuffer("(=> (< (pfntype ")
				.append( cmd.getTagset() ).append(") 2)  (or false " );
				for( MemoryCommand ev : evictings )
				{//TODO check pfntype of `ev' ??
					constraint.append("(=> (= (getPfn ").append( cmd.getValueOfTagset() )
					.append(") (getPfn ").append( ev.getValueOfTagset() ).append(")) ")
					.append("(= (getVPN ").append( cmd.getVirtualAddress() )
					.append(") (getVPN ").append( ev.getVirtualAddress() ).append(")))");
				}					
				postAssert( constraint.append("))").toString() );					
			}
			else if ( tlbRanges.get(cmd) instanceof InitialTlbMiss )
			{
				// ���� ������ ����� PFN\M
				StringBuffer constraint = new StringBuffer("(=> (< (pfntype ")
				.append( cmd.getTagset() ).append(") 2) (or false ");
				for( int p = 0; p < dtlb.getJTLBSize(); p++ )
				{
					if ( dtlb.getDTLB().contains(p) )
						continue;
					
					TLBRow r = dtlb.getRow(p);
					if ( r.getMask() != pfnLength )
						continue;
					// TODO ����� ������ � ������ ��������
					if ( dtlb.getPABITS() - pfnLength + 1 >= 30 )
					{
						if ( r.getVPNd2().intValue() != (int)Math.pow(2, dtlb.getSEGBITS()
								- dtlb.getPABITS() + pfnLength) - 1 )
							continue;
					}
					else
					{
						//������� 10 ��� ������ ����������� �������� == 1023
						if ( r.getVPNd2().shiftRight(30 - dtlb.getPABITS() + pfnLength).intValue()
								!= 255 )
							continue;
					}
							
					if ( r.getValid0() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN0().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 ).append(")))");
					}
					if ( r.getValid1() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN1().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 + 1 ).append(")))");
					}
				}
				postAssert( constraint.append("))").toString() );
			}
			else if ( tlbRanges.get(cmd) instanceof UnusefulTlbMiss )
			{
				// ���� ����� M[m1..m2]
				StringBuffer constraint = new StringBuffer("(=> (< (pfntype ")
				.append( cmd.getTagset() ).append(") 2) (or false ");
				for(
						int p = ((UnusefulTlbMiss)tlbRanges.get(cmd)).getMinimumM() - 1;
						p < ((UnusefulTlbMiss)tlbRanges.get(cmd)).getMaximumM();
						p++
					)
				{
					TLBRow r = dtlb.getRow(p);
					// TODO ����� ������ � ������ ��������
					if ( dtlb.getPABITS() - pfnLength + 1 >= 30 )
					{
						if ( r.getVPNd2().intValue() != (int)Math.pow(2, dtlb.getSEGBITS()
								- dtlb.getPABITS() + pfnLength) - 1 )
							continue;
					}
					else
					{
						//������� 10 ��� ������ ����������� �������� == 1023
						if ( r.getVPNd2().shiftRight(30 - dtlb.getPABITS() + pfnLength).intValue()
								!= 255 )
							continue;
					}
					if ( r.getMask() == pfnLength && r.getValid0() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN0().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 ).append(")))");
					}
					if ( r.getMask() == pfnLength && r.getValid1() == 1 )
					{
						constraint.append("(=> (= (mk-bv ").append( pfnLength )
						.append(" " ).append( r.getPFN1().longValue() ).append(") ")
						.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
						.append("(= (getVPN ").append( cmd.getVirtualAddress() )
						.append(") (mk-bv ").append( vpnLength ).append(" " )
						.append( r.getVPNd2().longValue() * 2 + 1 ).append(")))");
					}
				}
				postAssert( constraint.append("))").toString() );
			}
			else // tlbRanges.get(cmd) instanceof UsefulTlbMiss
			{
				// ���� ����� M[m]
				StringBuffer constraint = new StringBuffer("(=> (< (pfntype ")
				.append( cmd.getTagset() ).append(") 2) (or false ");
				TLBRow r = dtlb.getRow( ((UsefulTlbMiss)tlbRanges.get(cmd)).getM() - 1 );
				// TODO ����� ������ � ������ ��������
				if ( dtlb.getPABITS() - pfnLength + 1 >= 30 )
				{
					if ( r.getVPNd2().intValue() != (int)Math.pow(2, dtlb.getSEGBITS()
							- dtlb.getPABITS() + pfnLength) - 1 )
						return;
				}
				else
				{
					//������� 10 ��� ������ ����������� �������� == 1023
					if ( r.getVPNd2().shiftRight(30 - dtlb.getPABITS() + pfnLength).intValue()
							!= 255 )
						return;
				}
				if ( r.getMask() == pfnLength && r.getValid0() == 1 )
				{
					constraint.append("(=> (= (mk-bv ").append( pfnLength )
					.append(" " ).append( r.getPFN0().longValue() ).append(") ")
					.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
					.append("(= (getVPN ").append( cmd.getVirtualAddress() )
					.append(") (mk-bv ").append( vpnLength ).append(" " )
					.append( r.getVPNd2().longValue() * 2 ).append(")))");
				}
				if ( r.getMask() == pfnLength && r.getValid1() == 1 )
				{
					constraint.append("(=> (= (mk-bv ").append( pfnLength )
					.append(" " ).append( r.getPFN1().longValue() ).append(") ")
					.append( "(getPfn ").append( cmd.getValueOfTagset() ).append(")) ")
					.append("(= (getVPN ").append( cmd.getVirtualAddress() )
					.append(") (mk-bv ").append( vpnLength ).append(" " )
					.append( r.getVPNd2().longValue() * 2 + 1 ).append(")))");
				}
				postAssert( constraint.append("))").toString() );
			}
		}
	}

	private void loadstore2Constraints0(List<MemoryCommand> commands) {
		if ( commands.get(1).isLOAD() )
		{
			// p1 = p2 -> value1 = value2
			String value1;
			String value2;
			
			if ( commands.get(0).isSTORE() )
			{
				value1 = commands.get(0).getResult() + "0";
			}
			else
			{
				value1 = commands.get(0).getResult() + "1";					
			}
			if ( commands.get(1).getResult() != commands.get(0).getResult() )
			{
				value2 = commands.get(1).getResult() + "1";
			}
			else
			{
				value2 = commands.get(1).getResult() + "2";					
			}
			
			postAssert( new StringBuffer( "(=> (and (= ")
				.append( commands.get(0).getTagset() ).append(" " )
				.append( commands.get(1).getTagset() ).append(") (= ")
				.append( "(getCacheOffset " ).append( commands.get(0).getVirtualAddress() )
				.append(") (getCacheOffset ").append( commands.get(1).getVirtualAddress() )
				.append("))) (= ").append( value1 ).append(" " ).append( value2 )
				.append("))").toString() );
		}
	}

	private void loadstore2Constraints1(MemoryCommand command1, MemoryCommand command2)
	{
		if ( command2.isLOAD() )
		{
			// p1 = p2 -> value1 = value2
			postAssert( new StringBuffer( "(=> (and (= ")
				.append( command1.getTagset() ).append(" ")
				.append( command2.getTagset() )
				.append(") (= ")
				.append( "(getCacheOffset " ).append( command1.getVirtualAddress() )
				.append(") (getCacheOffset ").append( command2.getVirtualAddress() )
				.append("))) (= ").append( command1.getValue() ).append(" " )
				.append( command2.getValue() ).append("))").toString() );
		}
	}

	public void postAssert( String _assert )
	{
		yl.yicesl_read( context, "(assert " + _assert + ")" );
	}
	
	public void postDefine( String name, String type, String expression )
	{
		yl.yicesl_read( context, "(define " + name + " :: " + type + " " + expression + ")" );
	}
	
	public Set<Long> getLinterM()
	{
		Set<Long> result = new HashSet<Long>();
		
		for( int p : dtlb.getDTLB() )
		{
			TLBRow r = dtlb.getRow(p);
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN0().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN1().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
		}
		
		return result;		
	}

	public int getTagsetLength()
	{
		return tagsetLength;
	}
	
	public int getPfnLength()
	{
		return pfnLength;
	}

	public Set<Long> getLinterPFN() {
		Set<Long> result = new HashSet<Long>();
		
		for( int p = 0; p < dtlb.getJTLBSize(); p++ )
		{
			TLBRow r = dtlb.getRow(p);
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN0().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN1().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
		}
		
		return result;		
	}

	public Set<Long> getLinterPFNminusM() {
		Set<Long> result = new HashSet<Long>();
		
		for( int p = 0; p < dtlb.getJTLBSize(); p++ )
		{
			if ( dtlb.getDTLB().contains(p) )
				continue;
			
			TLBRow r = dtlb.getRow(p);
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN0().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN1().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
						result.add(l);
				}
			}
		}
		
		return result;		
	}

	/**
	 * ���������� ��� ����� ������ �������� � microDTLB
	 */
	public Set<Long> getMremains(int tagIndex) {
		Set<Long> result = new HashSet<Long>();
		for( int p = tagIndex + 1; p < dtlb.getDTLBSize(); p++ )
		{
			result.add( dtlb.getRow( dtlb.getDTLB().get(p) ).getPFN0().longValue() );
			result.add( dtlb.getRow( dtlb.getDTLB().get(p) ).getPFN1().longValue() );
		}
		return result;
	}

	/**
	 * ���������� L \inter [{M_tagIndex}]
	 */
	public Set<Long> getLinterM(int tagIndex) {
		Set<Long> result = new HashSet<Long>();
		
			TLBRow r = dtlb.getRow( dtlb.getDTLB().get( tagIndex ) );
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN0().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, dataL1.getTagBitLength() + dataL1.getSetNumberBitLength() - dtlb.getPFNBitLen() ) == pfn )
						result.add(l);
				}
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				long pfn = r.getPFN1().longValue();
				for( long l : dataL1.getValidTagsets() )
				{
					if ( l / (long)Math.pow(2, dataL1.getTagBitLength() + dataL1.getSetNumberBitLength() - dtlb.getPFNBitLen() ) == pfn )
						result.add(l);
				}
			}

		
		return result;		
	}

	public int getTLBAssociativity() {
		return tlbAssoc;
	}

	public Set<Long> getLinterMrange(int minimumM, int maximumM)
	{
		Set<Long> result = new HashSet<Long>();
		for( int m = minimumM; m <= maximumM; m++ )
		{
			result.addAll( getLinterM(m - 1) );
		}
		return result;
	}
	
	private int counter = 0;
	public synchronized long getUniqueNumber()
	{
		return ++counter;
	}

	public Set<Long> getMfull()
	{
		Set<Long> result = new HashSet<Long>();
		for( int rowIndex : dtlb.getDTLB() )
		{
			TLBRow r = dtlb.getRow( rowIndex );
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				result.add( r.getPFN0().longValue() );
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				result.add( r.getPFN1().longValue() );
			}
		}
		return result;
	}

	public Set<Long> getPFNminusMfull()
	{
		Set<Long> result = new HashSet<Long>();
		for( int rowIndex = 0; rowIndex < dtlb.getJTLBSize(); rowIndex++ )
		{
			if ( dtlb.getDTLB().contains( rowIndex ) )
				continue;
			
			TLBRow r = dtlb.getRow( rowIndex );
			if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				result.add( r.getPFN0().longValue() );
			}
			if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
			{
				result.add( r.getPFN1().longValue() );
			}
		}
		return result;
	}

	public Set<Long> getM(int i)
	{
		Set<Long> result = new HashSet<Long>();
		
		TLBRow r = dtlb.getRow( dtlb.getDTLB().get(i) );

		if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
		{
			result.add( r.getPFN0().longValue() );
		}
		if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
		{
			result.add( r.getPFN1().longValue() );
		}
		
		return result;
	}

	/** [ [M] \inter L<index>, i.e. {ts: ^ts^ \in M /\ ts is i'th in cache set} +> tail of this set] */
	public Map<Long, Set<Long>> getLindexInterM( int section )
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		for( int p : dtlb.getDTLB() )
		{
			result.putAll( getLindexInterPFNindex(section, p) );
		}
		
		return result;		
	}

	private void cacheSearch0(Map<Long, Set<Long>> result, int section, long pfn)
	{
		for( int set = 0; set < (long)Math.pow(2, dataL1.getSetNumberBitLength()); set++ )
		{
			long l = dataL1.getTag(section, set) * (long)Math.pow(2, dataL1.getSetNumberBitLength()) + set;
			//TODO ��� ���� � ��-valid ?
			if ( l / (long)Math.pow(2, tagsetLength - pfnLength ) == pfn )
			{
				Set<Long> tail = new HashSet<Long>();
				for( int s = section+1; s < dataL1.getSectionNumber(); s++ )
				{
					tail.add(
						dataL1.getTag(s, set) * (long)Math.pow(2, dataL1.getSetNumberBitLength()) + set
					);
				}
				result.put( l, tail );
			}
		}
	}

	public Map<Long, Set<Long>> getLindexInterPFN(int section)
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		for( int p = 0; p < dtlb.getJTLBSize(); p++ )
		{
			result.putAll( getLindexInterPFNindex(section, p) );
		}
		
		return result;		
	}

	public Map<Long, Set<Long>> getLindexInterPFNminusM(int section )
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		for( int p = 0; p < dtlb.getJTLBSize(); p++ )
		{
			if ( dtlb.getDTLB().contains(p) )
				continue;
			result.putAll( getLindexInterPFNindex(section, p) );
		}
		
		return result;		
	}

	public Map<Long, Set<Long>> getLindexInterMrange(int section, int minM, int maxM)
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		for( int p = minM; p < maxM; p++ )////TODO may be `<=' ?
		{
			result.putAll( getLindexInterPFNindex(section, p) );
		}
		
		return result;		
	}

	public Map<Long, Set<Long>> getLindexInterPFNindex(int cacheSection, int tlbIndex)
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		TLBRow r = dtlb.getRow(tlbIndex);
		if ( r.getValid0() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
		{
			cacheSearch0( result, cacheSection, r.getPFN0().longValue() );
		}
		if ( r.getValid1() == 1 && r.getMask().intValue() == dtlb.getPFNBitLen() )
		{
			cacheSearch0( result, cacheSection, r.getPFN1().longValue() );
		}
		
		return result;		
	}

	public TLBRange getTLBRange(MemoryCommand cmd)
	{
		return tlbRanges.get(cmd);
	}
	
	public L1Range getL1Range( MemoryCommand cmd )
	{
		return l1Ranges.get(cmd);
	}

	public Map<Long, Set<Long>> getLinterPFNminusMrange(int i1, int i2)
	{
		Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
		
		for( int p = 0; p < dtlb.getJTLBSize(); p++ )
		{
			if ( dtlb.getDTLB().contains(p) )
				continue;
			
			for( int section = i1; section <= i2 ; section++ )
			{
				result.putAll( getLindexInterPFNindex(section, p) );
			}
		}
		
		return result;		
	}

}

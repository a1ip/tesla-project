package ru.teslaprj.ranges.ts;

import ru.teslaprj.ranges.L1Range;
import ru.teslaprj.ranges.TLBRange;
import ru.teslaprj.scheme.MemoryCommand;

public class InitialTlbHit extends TLBRange {

	public InitialTlbHit(MemoryCommand cmd) {
		super(cmd);
	}

	@Override
	public void visit(L1Range r)
	{
		r.visitInitialTlbHit(this);
	}

}

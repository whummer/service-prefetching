package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.impl.Context;
import io.hummer.prefetch.impl.TimeClock;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.sim.VehicleSimulation.PathPoint;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Automatic pre-fetching strategy. Context-dependent.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
public class PrefetchStrategyContextBased extends PrefetchStrategy {

	private ServiceInvocation affectedInvocation;
	private UsagePattern usagePattern;
	private double maxTimeLookIntoFuture = 3;

	public PrefetchStrategyContextBased(ServiceInvocation inv, 
			UsagePattern usage, double maxTimeLookIntoFuture) {
		this.affectedInvocation = inv;
		this.usagePattern = usage;
		this.maxTimeLookIntoFuture = maxTimeLookIntoFuture;
	}

	@SuppressWarnings("unchecked")
	public boolean doPrefetchNow(Object context) {
		//System.out.println("affectedInvocation.prefetchPossible " + affectedInvocation.prefetchPossible);
		if(!affectedInvocation.prefetchPossible) {
			//System.out.println("not possible: " + Util.toString(affectedInvocation.serviceCall));
			return false;
		}
		Context<Object> ctx = (Context<Object>)context;
		List<PathPoint> path = (List<PathPoint>)ctx.
				getAttribute(Context.ATTR_FUTURE_PATH);
		//System.out.println("path in future: " + path);
		if(path != null) {
			double timeNow = (Double)ctx.getAttribute(Context.ATTR_TIME);
			for(PathPoint pt : path) {
				if(pt.time.time > timeNow + maxTimeLookIntoFuture) {
					break;
				}
				double timeThen = pt.time.time;
//				System.out.println("time " + pt.time.time + " coverage: " + 
//						pt.cellNetworkCoverage.hasAnyCoverage() + 
//						", we need: " + usagePattern.predictUsage(timeThen));

				/* if there is no network ... */
				if(!pt.cellNetworkCoverage.hasAnyCoverage()) {
					/* ... and if we need a network */
					if(usagePattern.predictUsage(timeThen) > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public Double getNextAskTimeDelayInSecs() {
		return 0.0;
	}

	public void notifyPrefetchPerformed() {
		lastTime = TimeClock.now();
	}
}

package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.Path;
import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.prefetch.context.Time;
import io.hummer.prefetch.context.TimeClock;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.util.log.LogUtil;
import io.hummer.util.xml.XMLUtil;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

/**
 * Automatic pre-fetching strategy. Context-dependent.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
public class PrefetchStrategyContextBased extends PrefetchStrategy {

	private static final Logger LOG = LogUtil.getLogger();
	
	private static final int DEFAULT_TIME_STEPS_LOOK_FUTURE = 10;
	private static final double DEFAULT_TIME_STEPS_DURATION = 10.0;

//	private ServiceInvocation affectedInvocation;
	private UsagePattern usagePattern;
	private double timeStepDuration;
	private int timeStepsLookIntoFuture;

	public PrefetchStrategyContextBased(UsagePattern usage) {
		this(usage, DEFAULT_TIME_STEPS_LOOK_FUTURE,
				DEFAULT_TIME_STEPS_DURATION);
	}

	public PrefetchStrategyContextBased(
			UsagePattern usage, 
			int timeStepsLookIntoFuture,
			double timeStepDuration, String id) {
		this(usage, timeStepsLookIntoFuture, timeStepDuration);
		this.id = id;
	}
	public PrefetchStrategyContextBased(
			UsagePattern usage, 
			int timeStepsLookIntoFuture,
			double timeStepDuration) {
		this.usagePattern = usage;
		this.timeStepsLookIntoFuture = timeStepsLookIntoFuture;
		this.timeStepDuration = timeStepDuration;
	}

	@SuppressWarnings("unchecked")
	public boolean doPrefetchNow(Object context) {

		//System.out.println("affectedInvocation.prefetchPossible " + affectedInvocation.prefetchPossible);
//		if(!affectedInvocation.prefetchPossible) {
//			//System.out.println("not possible: " + Util.toString(affectedInvocation.serviceCall));
//			return false;
//		}

		Context<Object> ctx = (Context<Object>)context;
		Path path = (Path)ctx.getAttribute(Context.ATTR_PATH);
		//System.out.println("path in future: " + path);
		if(path != null) {
//			System.out.println("doPrefetchNow: " + (path.size()));
			Time timeNow = (Time)ctx.getAttribute(Context.ATTR_TIME);
//			int count = 0;
			double t = timeNow.time;
			for(int i = 0; i < timeStepsLookIntoFuture; i ++) {
				t += timeStepDuration;
				
				PathPoint pt = path.getLocationAtTime(t);
//				System.out.println(pt.time.time + " - " + timeNow.time + " - " + maxTimeLookIntoFuture);
//				if(pt.time.time > timeNow.time + maxTimeLookIntoFuture) {
//					break;
//				}
//				if(++count > timePointsLookIntoFuture) {
//					break;
//				}
				
//				double timeThen = pt.time.time;
				
				if(LOG.isTraceEnabled()) {
					LOG.trace("time " + pt.time.time + " (" + t + ") coverage : " + 
						pt.cellNetworkCoverage.hasSufficientCoverage() + 
						", we need: " + usagePattern.predictUsage(t));
				}
//				LOG.info("doPrefetchNow 1: " + pt.cellNetworkCoverage.hasAnyCoverage());

				/* if there is no network ... */
				try {
					if(!pt.cellNetworkCoverage.hasSufficientCoverage()) {
						/* ... and if we need a network 
						 * IMPORTANT: Do NOT add the second check (usage > 0). */
						//double usage = usagePattern.predictUsage(t);
						//if(usage > 0) {
							return true;
						//}
					}
				} catch (Exception e) {
					System.out.println(new XMLUtil().toString(pt));
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

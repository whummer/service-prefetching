package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.client.ServiceInvocationBuilder.TemplateBasedInvocationBuilder;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.ContextPredictor;
import io.hummer.prefetch.context.Time;
import io.hummer.util.coll.Pair;
import io.hummer.util.log.LogUtil;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * "Predicts" and generates future invocations (requests) for a particular service.
 * @author Waldemar Hummer
 */
public abstract class InvocationPredictor {

	double timeVicinityIntoFutureSecs = 10;

	public abstract List<Pair<Context<Object>, ServiceInvocation>> predictInvocations(
			Context<Object> currentContext, Time fromTime, Time toTime);

	public final List<Pair<Context<Object>, ServiceInvocation>> predictInvocations(
			Context<Object> currentContext, Time time) {
		return predictInvocations(currentContext, time, time.add(timeVicinityIntoFutureSecs));
	}

	public final Pair<Context<Object>, ServiceInvocation> predictInvocation(
			Context<Object> currentContext, Time time) {
		List<Pair<Context<Object>, ServiceInvocation>> invs = predictInvocations(
				currentContext, time, time.add(timeVicinityIntoFutureSecs));
		//System.out.println("predict invocations " + currentContext + " - " + invs);
		if(invs.isEmpty())
			return null;
		if(invs.size() > 1) 
			throw new RuntimeException("Expected 1 invocation at time " + 
					time + ", got: " + invs.size());
		return invs.get(0);
	}

	/**
	 * Implementation of {@link InvocationPredictor} based on an 
	 * invocation template and (predicted) future context information.
	 */
	public static class TemplateBasedInvocationPredictor extends InvocationPredictor {
		private ContextPredictor<Object> contextPredictor;
		private TemplateBasedInvocationBuilder builder;

		private static final Logger LOG = LogUtil.getLogger();

		public TemplateBasedInvocationPredictor(String template, 
				ContextPredictor<Object> contextPredictor, double timeVicinityIntoFutureSecs) {
			this.contextPredictor = contextPredictor;
			this.builder = new TemplateBasedInvocationBuilder(template);
			this.timeVicinityIntoFutureSecs = timeVicinityIntoFutureSecs;
		}
		public List<Pair<Context<Object>, ServiceInvocation>> predictInvocations(
				Context<Object> currentContext, Time fromTime, Time toTime) {
			List<Pair<Context<Object>, ServiceInvocation>> result = new LinkedList<>();
			for(Context<Object> ctx : contextPredictor.predictContexts(currentContext, fromTime, toTime)) {
				result.add(new Pair<>(ctx, builder.buildInvocation(ctx)));
			}
			LOG.trace("predicted [" + fromTime.time + "," + 
					toTime.time + "]: " + result.size());
			return result;
		}

		@Override
		public String toString() {
			return "TBInvPred " + builder.getTemplate();
		}
	}

}

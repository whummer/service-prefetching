package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.client.ServiceInvocationBuilder.TemplateBasedInvocationBuilder;
import io.hummer.prefetch.context.ContextPredictor;

import java.util.List;

/**
 * "Predicts" and generates future invocations (requests) for a particular service.
 * @author Waldemar Hummer
 */
public interface InvocationPredictor {

	List<ServiceInvocation> predictInvocations(double futureTime);

	/**
	 * Implementation of {@link InvocationPredictor} based on an 
	 * invocation template and (predicted) future context information.
	 */
	public static class TemplateBasedInvocationPredictor implements InvocationPredictor {
		private ContextPredictor<Object> contextPredictor;
		private TemplateBasedInvocationBuilder builder;

		public TemplateBasedInvocationPredictor(String template, ContextPredictor<Object> contextPredictor) {
			this.contextPredictor = contextPredictor;
			this.builder = new TemplateBasedInvocationBuilder(template);
		}
		public List<ServiceInvocation> predictInvocations(double futureTime) {
			//return builder.buildInvocation(contextPredictor.predict(futureTime));
			// TODO
			return null;
		}
		
	}

}

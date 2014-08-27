package io.hummer.prefetch.client;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.context.Context;
import io.hummer.util.xml.XMLUtil;

/**
 * Most prefetching tasks will have to prefetch similar but slightly different 
 * service invocations over time (e.g., where there current time or location 
 * is encoded into the invocation). 
 * 
 * Hence, instead of repeating the exact same service invocation over and over, 
 * we allow the user to create an invocation builder, which constructs a service
 * invocation for a specific time and context.
 * 
 * @author Waldemar Hummer
 */
public abstract class ServiceInvocationBuilder extends ServiceInvocation {

	public abstract ServiceInvocation buildInvocation(Context<Object> context);
	private static XMLUtil xmlUtil = new XMLUtil();

	public static class TemplateBasedInvocationBuilder extends ServiceInvocationBuilder {
		String template;
		public TemplateBasedInvocationBuilder(String template) {
			this.template = template;
		}
		public ServiceInvocation buildInvocation(Context<Object> context) {
			String tmp = template;
			for(String key: context.keySet()) {
				tmp = tmp.replace("{{" + key + "}}", "" + context.getAttribute(key));
			}
			try {
				return xmlUtil.toJaxbObject(ServiceInvocation.class, xmlUtil.toElement(tmp));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	};
}

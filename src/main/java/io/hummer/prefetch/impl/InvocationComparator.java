package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.util.xml.XMLUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

/**
 * Allows to compare service invocations, in order to determine whether
 * an old (prefetched) service invocation result satisfies the requests
 * of a new service invocation.
 * 
 * @author Waldemar Hummer
 */
public abstract class InvocationComparator {

    private static final Logger LOG = Logger.getLogger(InvocationComparator.class);
   
	public abstract List<ServiceInvocation> match(
			Collection<ServiceInvocation> haystack, ServiceInvocation needle);

	public ServiceInvocation matchOne(
			Collection<ServiceInvocation> haystack, ServiceInvocation needle) {
		List<ServiceInvocation> r = match(haystack, needle);
		if(r.isEmpty())
			return null;
		if(r.size() > 1)
			LOG.warn("Multiple service invocations matching '" + needle + "': " + r);
		return r.get(0);
	}

	public static class DefaultComparator extends InvocationComparator {

		@Override
		public List<ServiceInvocation> match(
				Collection<ServiceInvocation> haystack, ServiceInvocation needle) {
			List<ServiceInvocation> result = new LinkedList<ServiceInvocation>();
			DefaultWrapper wrapped = new DefaultWrapper(needle);
			for(ServiceInvocation i : haystack) {
//				System.out.println("wrapped.equals(i): "
//						+ "\n" + Util.toString(i) + " - \n"
//						+ wrapped.equals(i));
				if(wrapped.equals(i)) {
					result.add(i);
				}
			}
			return result;
		}
		
	}

	/** 
	 * Service invocation wrapper with equals(...) and hashCode()
	 */
	public static class DefaultWrapper extends ServiceInvocation {
		ServiceInvocation wrapped;
		XMLUtil xmlUtil = new XMLUtil();
		public DefaultWrapper(ServiceInvocation inv) {
			this.prefetchPossible = inv.prefetchPossible;
			this.serviceCall = inv.serviceCall;
			this.serviceEPR = inv.serviceEPR;
			this.wrapped = inv;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((serviceCall == null) ? 0 : xmlUtil.toStringCanonical((Element)serviceCall).hashCode());
			result = prime * result
					+ ((serviceEPR == null) ? 0 : 
						W3CEndpointReferenceUtils.getAddress(serviceEPR).hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof ServiceInvocation))
				return false;
			ServiceInvocation other = (ServiceInvocation) obj;
			//System.out.println(xmlUtil.toStringCanonical((Element)serviceCall));
			//System.out.println(xmlUtil.toStringCanonical((Element)other.serviceCall));
			if (serviceCall == null) {
				if (other.serviceCall != null)
					return false;
			} else if (!xmlUtil.toStringCanonical((Element)serviceCall).equals(
							xmlUtil.toStringCanonical((Element)other.serviceCall)))
				return false;
			if (serviceEPR == null) {
				if (other.serviceEPR != null)
					return false;
			} else if (!W3CEndpointReferenceUtils.getAddress(serviceEPR).equals(
					W3CEndpointReferenceUtils.getAddress(other.serviceEPR)))
				return false;
			return true;
		}
	}
}

package io.hummer.prefetch;

import io.hummer.prefetch.impl.InvocationPredictor;

import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

/**
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@WebService(targetNamespace = PrefetchingService.NAMESPACE)
public interface PrefetchingService {

	public static final String NAMESPACE = "http://simpli-city.eu/prefetch";

	/**
	 * Web service interface for clients that want to be notified 
	 * (push-mode) about new data prefetching results.
	 */
	@WebService(targetNamespace = PrefetchingService.NAMESPACE)
	public interface PrefetchingResultReceiver {
		void notify(PrefetchNotification notification);
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
	public static class PrefetchNotification {
		public String subscriptionID;
		@XmlJavaTypeAdapter(JaxbAdapter.class)
		public ServiceInvocation serviceInvocation;
		@XmlJavaTypeAdapter(JaxbAdapter.class)
		public SOAPEnvelope result;
	}

	public static class JaxbAdapter extends XmlAdapter<Object,Object> {
		public static XmlAdapter<Object,Object> actualAdapter;
		public Object marshal(Object v) throws Exception {
			if(actualAdapter == null)
				return null;
			return actualAdapter.marshal(v);
		}
		public Object unmarshal(Object v) throws Exception {
			if(actualAdapter == null)
				return null;
			return actualAdapter.unmarshal(v);
		}
		
	}

	@XmlRootElement(name="inv")
	@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
	public static class ServiceInvocation {
		/**
		 * The endpoint address of the service to prefetch.
		 */
		public W3CEndpointReference serviceEPR;
//		/**
//		 * Returns whether prefetching this 
//		 * invocation is possible or not.
//		 */
//		@XmlAttribute
//		public boolean prefetchPossible;
		/**
		 * The service call to perform.
		 */
		@XmlAnyElement
		//@XmlJavaTypeAdapter(JaxbAdapter.class)
		public Object serviceCall;
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
	public static class PrefetchRequest {
		/** 
		 * Should be null if this is a new request. 
		 * Non-null to update existing subscription. 
		 */
		public String subscriptionID;
		///**
		// * The service invocation to prefetch.
		// */
		//public ServiceInvocation invocation = new ServiceInvocation();
		/**
		 * The predictor responsible for creating future requests.
		 */
		@XmlTransient
		public InvocationPredictor invocationPredictor;
		/**
		 * The remote endpoint address of the service to notify about  
		 * prefetching results. May be null (for polling mode).
		 */
		public W3CEndpointReference notifyRemote;
		/**
		 * The local receiver to notify about prefetching 
		 * results. May be null (for polling mode).
		 */
		@XmlTransient
		public PrefetchingResultReceiver notifyLocal;
		/**
		 * The prefetching strategy to apply.
		 */
		@XmlJavaTypeAdapter(JaxbAdapter.class)
		public PrefetchStrategy strategy;
		/**
		 * The importance of this service invocation; 
		 * higher value means more important.
		 */
		public double lookIntoFutureSecs = 60*10;
		/**
		 * The importance of this service invocation; 
		 * higher value means more important.
		 */
		public double importance;
		/**
		 * The time criticality of this service invocation; 
		 * range from 0 (can be executed at any time) 
		 * to 1 (can only be requested at the exact time).
		 */
		public double timeCriticality;

		/* helper method */
		public PrefetchRequest setNotifyLocal(
				PrefetchingResultReceiver receiver) {
			notifyLocal = receiver;
			return this;
		}
	}

	@XmlRootElement
	@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
	public static class PrefetchResponse {
		public String subscriptionID;
	}

	@SOAPBinding(parameterStyle = ParameterStyle.BARE)
	PrefetchResponse setPrefetchingStrategy(PrefetchRequest request);
}

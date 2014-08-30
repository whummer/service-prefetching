package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.Time;
import io.hummer.prefetch.context.TimeClock;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;
import io.hummer.util.coll.Pair;
import io.hummer.util.xml.XMLUtil;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jws.WebService;
import javax.xml.soap.SOAPEnvelope;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

/**
 * Implementation of the prefetching service.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@WebService(
		endpointInterface = "io.hummer.prefetch.PrefetchingService",
		targetNamespace = PrefetchingService.NAMESPACE
)
public class PrefetchingServiceImpl implements PrefetchingService {

	protected final Map<String,PrefetchSubscription> prefetchings = new ConcurrentHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean(true);
	private static final Logger LOG = Logger.getLogger(PrefetchingServiceImpl.class);
	private static final XMLUtil xmlUtil = new XMLUtil();

    private Context<Object> context;

    static {
    	/* set the concrete XML adapter. */
    	PrefetchingService.JaxbAdapter.actualAdapter = 
    			new io.hummer.prefetch.ws.JaxbAdapter();
    }

    public PrefetchingServiceImpl(Context<Object> ctx) {
    	this.context = ctx;
	}

	/**
	 * Internal class which holds a prefetching subscription.
	 */
	protected static class PrefetchSubscription {
		public String subscriptionID;
		public PrefetchRequest request;
	}

	/**
	 * Timer task to schedule prefetching requests.
	 */
	private class PrefetchTask extends TimerTask {
		private String subscriptionID;
		public PrefetchTask(String subscriptionID) {
			this.subscriptionID = subscriptionID;
		}
		public void run() {
			PrefetchingServiceImpl.this.handleRequest(subscriptionID);
		}
	}

	/**
	 * Central Web service method called by the clients.
	 */
	public PrefetchResponse setPrefetchingStrategy(PrefetchRequest request) {
		final PrefetchSubscription s = new PrefetchSubscription();
		s.request = request;
		if(request.subscriptionID != null) {
			s.subscriptionID = request.subscriptionID;
		} else {
			s.subscriptionID = UUID.randomUUID().toString();
			request.subscriptionID = s.subscriptionID;
		}

		prefetchings.put(s.subscriptionID, s);
		TimeClock.schedule(new PrefetchTask(s.subscriptionID), 0);

		PrefetchResponse result = new PrefetchResponse();
		result.subscriptionID = s.subscriptionID;
		return result;
	}

	/**
	 * Handle a prefetching request. 
	 * Calls to this method are triggered by the timer task of this class.
	 * @param subscriptionID
	 */
	private void handleRequest(final String subscriptionID) {
		PrefetchSubscription pf = prefetchings.get(subscriptionID);
		if(pf == null) {
			LOG.warn("Subscription ID " + subscriptionID + " not found.");
			return;
		}
		handleRequest(pf);
	}

	/**
	 * Handle a prefetching request. 
	 * @param pf
	 */
	protected void handleRequest(PrefetchSubscription pf) {
		LOG.trace("handle request: " + pf.request.invocationPredictor);
//		if(pf.request.strategy == null) {
//			pf.request.strategy = new PrefetchStrategyPeriodic();
//		}
		boolean doPrefetch = pf.request.strategy.doPrefetchNow(context);
		LOG.debug("do prefetch (" + context.getTime() + "): " + doPrefetch);
		if(doPrefetch) {
			if(running.get()) {
				Boolean hasNetwork = (Boolean)context.getAttribute(Context.ATTR_NETWORK_AVAILABLE);
				if(hasNetwork != null && !hasNetwork) {
					LOG.debug("We should prefetch now, but have no network connectivity...");
					return;
				}
//				String address = W3CEndpointReferenceUtils.getAddress(
//						pf.request.invocation.serviceEPR);
//				LOG.info("Initiate prefetching of service: " + address);
				/* do prefetching */
				doPrefetch(pf.request);
				/* notify strategy */
				pf.request.strategy.notifyPrefetchPerformed();
			}
		}
		if(running.get()) {
			/* schedule next prefetching */
			Double delay = pf.request.strategy.getNextAskTimeDelayInSecs();
			if(delay != null && delay > 0) {
				//timer.schedule(new PrefetchTask(pf.subscriptionID), delay);
				LOG.debug("scheduling next prefetch decision in " + delay + " seconds");
				TimeClock.schedule(new PrefetchTask(pf.subscriptionID), delay);
			}
		}
	}

	/**
	 * Perform the actual prefetching.
	 * @param request
	 */
	private void doPrefetch(PrefetchRequest request) {
//		System.out.println("foo");
		Time currentTime = (Time)context.getAttribute(Context.ATTR_TIME);
		if(currentTime != null) {
			List<Pair<Context<Object>, ServiceInvocation>> invs = request.
					invocationPredictor.predictInvocations(context, currentTime, 
							currentTime.add(request.lookIntoFutureSecs));
//			System.out.println("predict: " + invs.size());

			for(Pair<Context<Object>, ServiceInvocation> inv : invs) {
				Context<Object> ctx = inv.getFirst();
//				String TODO = (inv + " unavailable: " + ctx.isNetworkUnavailable());
				//System.out.println(TODO);
				if(ctx.isNetworkUnavailable()) {
					LOG.debug("here unavailable: " + 
							inv.getFirst().getAttribute(Context.ATTR_TIME) + 
							" - req: " + xmlUtil.getSOAPBodyAsString(
									(Element)inv.getSecond().serviceCall));
					doPrefetch(request, inv.getSecond());
				} else {
					LOG.debug("here available: " + 
							inv.getFirst().getAttribute(Context.ATTR_TIME) + 
							" - req: " + xmlUtil.getSOAPBodyAsString(
									(Element)inv.getSecond().serviceCall));
				}
			}
		}
	}
	private void doPrefetch(PrefetchRequest request, ServiceInvocation invocation) {
		try {
			/* check if this is a dynamic invocation builder */
//			if(invocation instanceof ServiceInvocationBuilder) {
//				invocation = ((ServiceInvocationBuilder)invocation).
//						buildInvocation(context);
//			}

			/* perform invocation */
			SOAPEnvelope env = WSClient.toEnvelope(
					(Element)invocation.serviceCall);
			SOAPEnvelope response = WSClient.invokePOST(
					invocation.serviceEPR, env);

			/* notify subscriber(s) */
			PrefetchNotification notification = new PrefetchNotification();
			notification.subscriptionID = request.subscriptionID;
			notification.serviceInvocation = invocation;
			notification.result = response;
			if(request.notifyRemote != null) {
				PrefetchingResultReceiver subscriber = 
						WSClient.createClientJaxws(PrefetchingResultReceiver.class, new URL(
								W3CEndpointReferenceUtils.getAddress(request.notifyRemote) + "?wsdl"));
				subscriber.notify(notification);
			}
			if(request.notifyLocal != null) {
				LOG.debug("Notifying listener of prefetch result for request: " + 
						//request.notifyLocal
						//new XMLUtil().toString(notification)
						new XMLUtil().toString(env));
				request.notifyLocal.notify(notification);
			}
			
		} catch (Exception e) {
			LOG.warn("Unable to invoke service: " + 
					invocation, e);
		}
	}

	protected SOAPEnvelope performInvocation(ServiceInvocation inv) throws IOException {
		SOAPEnvelope env = WSClient.toEnvelope(
				(Element)inv.serviceCall);
		SOAPEnvelope response = WSClient.invokePOST(
				inv.serviceEPR, env);
		return response;
	}

}
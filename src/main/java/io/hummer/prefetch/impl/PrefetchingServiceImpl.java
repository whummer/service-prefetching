package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService;
import io.hummer.prefetch.client.ServiceInvocationBuilder;
import io.hummer.prefetch.strategy.PrefetchStrategyPeriodic;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.TimerTask;
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
		endpointInterface = "eu.simpli_city.ctx_personalize.interfaces.PrefetchingService",
		targetNamespace = PrefetchingService.NAMESPACE
)
public class PrefetchingServiceImpl implements PrefetchingService {

	protected final Map<Long,PrefetchSubscription> prefetchings = new ConcurrentHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean(true);
	private static final Logger LOG = Logger.getLogger(PrefetchingServiceImpl.class);

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
		public long subscriptionID;
		public PrefetchRequest request;
	}

	/**
	 * Timer task to schedule prefetching requests.
	 */
	private class PrefetchTask extends TimerTask {
		private long subscriptionID;
		public PrefetchTask(long subscriptionID) {
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
			s.subscriptionID = System.currentTimeMillis();
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
	private void handleRequest(final long subscriptionID) {
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
		//System.out.println(pf.request.strategy);
		if(pf.request.strategy == null) {
			pf.request.strategy = new PrefetchStrategyPeriodic();
		}
		boolean doPrefetch = pf.request.strategy.doPrefetchNow(context);
		if(doPrefetch) {
			if(running.get()) {
				Boolean hasNetwork = (Boolean)context.getAttribute(Context.ATTR_NETWORK_AVAILABLE);
				if(hasNetwork != null && !hasNetwork) {
					LOG.debug("We should prefetch now, but have no network connectivity...");
					return;
				}
				String address = W3CEndpointReferenceUtils.getAddress(
						pf.request.invocation.serviceEPR);
				LOG.info("Initiate prefetching of service: " + address);
				/* do prefetching */
				doPrefetch(pf.request);
				/* notify strategy */
				pf.request.strategy.notifyPrefetchPerformed();
				/* schedule next prefetching */
				Double delay = pf.request.strategy.getNextAskTimeDelayInSecs();
				if(delay != null && delay > 0) {
					//timer.schedule(new PrefetchTask(pf.subscriptionID), delay);
					TimeClock.schedule(new PrefetchTask(pf.subscriptionID), delay);
				}
			}
		}
	}

	/**
	 * Perform the actual prefetching.
	 * @param request
	 */
	private void doPrefetch(PrefetchRequest request) {
		ServiceInvocation invocation = request.invocation;
		try {
			/* check if this is a dynamic invocation builder */
			if(invocation instanceof ServiceInvocationBuilder) {
				invocation = ((ServiceInvocationBuilder)invocation).
						buildInvocation(context);
			}

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
				LOG.info("Notifying listener of prefetch result: " + request.notifyLocal);
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
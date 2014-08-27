package io.hummer.prefetch.client;

import io.hummer.osm.util.Util;
import io.hummer.prefetch.PrefetchingService.PrefetchingResultReceiver;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.Context.ContextChangeListener;
import io.hummer.prefetch.impl.InvocationComparator;
import io.hummer.prefetch.impl.PrefetchingServiceImpl;
import io.hummer.prefetch.impl.TimeClock;
import io.hummer.prefetch.sim.util.AuditEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.soap.SOAPEnvelope;

import org.apache.log4j.Logger;

import io.hummer.util.coll.CollectionsUtil.MapBuilder;

public class PrefetchingCapableClient 
	extends PrefetchingServiceImpl 
	implements PrefetchingResultReceiver, ContextChangeListener<Object> {

    private static final Logger LOG = Logger.getLogger(PrefetchingCapableClient.class);

	private Context<Object> context;
	private Map<ServiceInvocation,PrefetchEntry> prefetchedResults = new HashMap<>();

	private static class PrefetchEntry {
		SOAPEnvelope result;
		double prefetchTime;
		@Override
		public String toString() {
			return "PrefetchEntry[result=" + result + ",time=" + prefetchTime + "]";
		}
	}

	public PrefetchingCapableClient(Context<Object> context) {
		super(context);
		this.context = context;
    	context.addChangeListener(this);
	}

	public SOAPEnvelope invoke(ServiceInvocation inv) throws IOException {
		return invoke(inv, new InvocationComparator.DefaultComparator());
	}
	public SOAPEnvelope invoke(ServiceInvocation inv, InvocationComparator comp) throws IOException {
		double ctxTime = (double)context.getAttribute(Context.ATTR_TIME);
		try {
			Boolean netAvail = (Boolean)context.getAttribute(Context.ATTR_NETWORK_AVAILABLE);
			if(netAvail != null && netAvail) {
				SOAPEnvelope res = performInvocation(inv);
				AuditEvent.addEvent(ctxTime, AuditEvent.E_INV_SUCCESS);
				return res;
			}
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("Cannot invoke service, "
					+ "trying to get prefetched result"); // TODO logging
			throw e;
		}
		AuditEvent.addEvent(ctxTime, AuditEvent.E_INV_FAILED);
		ServiceInvocation existing = comp.matchOne(prefetchedResults.keySet(), inv);
		if(existing == null) {
			AuditEvent.addEvent(ctxTime, AuditEvent.E_PREFETCH_MISS);
			if(!prefetchedResults.isEmpty())
				System.out.println("prefetchedResults: " + 
						Util.toString(prefetchedResults.keySet().iterator().next()));
			throw new IllegalStateException(this + " - Network unavailable and "
					+ "currently no prefetched result available "
					+ "for service invocation: " + Util.toString(inv));
		}
		SOAPEnvelope result = prefetchedResults.get(existing).result;
		AuditEvent.addEvent(ctxTime, AuditEvent.E_PREFETCH_HIT, 
				MapBuilder.map(Context.ATTR_TIME, 
						prefetchedResults.get(existing).prefetchTime));
		return result;
	}

	@Override
	public PrefetchResponse setPrefetchingStrategy(
			PrefetchRequest request) {
		PrefetchResponse r = super.setPrefetchingStrategy(
				request.setNotifyLocal(this));
		return r;
	}

	public Context<Object> getContext() {
		return context;
	}

	@Override
	public void notify(PrefetchNotification notification) {
		if(notification.result != null) {
			ServiceInvocation existing = new InvocationComparator.DefaultComparator()
				.matchOne(prefetchedResults.keySet(), notification.serviceInvocation);
			PrefetchEntry entry = new PrefetchEntry();
			entry.prefetchTime = TimeClock.now();
			entry.result = notification.result;
			if(existing == null) 
				prefetchedResults.put(notification.serviceInvocation, entry);
			else
				prefetchedResults.put(existing, entry);
			//System.out.println("prefetchedResults: " + prefetchedResults);
		}
	}

	public void onContextChanged(Map<String,Object> attrs, Map<String,Object> oldAttrs) {
		LOG.debug("Context has changed. Checking if we need to prefetch.");
		for(PrefetchSubscription sub : prefetchings.values()) {
			handleRequest(sub);
		}
	}
}

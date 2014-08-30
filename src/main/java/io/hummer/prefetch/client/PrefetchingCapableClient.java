package io.hummer.prefetch.client;

import io.hummer.osm.util.Util;
import io.hummer.prefetch.PrefetchingService.PrefetchingResultReceiver;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.TimeClock;
import io.hummer.prefetch.context.Context.ContextChangeListener;
import io.hummer.prefetch.context.Time;
import io.hummer.prefetch.impl.InvocationComparator;
import io.hummer.prefetch.impl.PrefetchingServiceImpl;
import io.hummer.prefetch.sim.util.AuditEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.soap.SOAPEnvelope;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import io.hummer.util.coll.CollectionsUtil.MapBuilder;
import io.hummer.util.xml.XMLUtil;

public class PrefetchingCapableClient 
	extends PrefetchingServiceImpl 
	implements PrefetchingResultReceiver, ContextChangeListener<Object> {

    private static final Logger LOG = Logger.getLogger(PrefetchingCapableClient.class);
    private static final XMLUtil xmlUtil = new XMLUtil();

	private Context<Object> context;
	private Map<ServiceInvocation,PrefetchEntry> prefetchedResults = new HashMap<>();
	private boolean deleteCachedResultsWhenReturned = false;
	private double lastCleanupTime = 0;
	private double deleteCachedResultsAfter = 20*60; // TODO make configurable

	private static class PrefetchEntry {
		SOAPEnvelope result;
		double prefetchTime;
		final List<Double> accessTimes = new LinkedList<>();
		@Override
		public String toString() {
			return "PrefetchEntry[result=" + result + ",time=" + prefetchTime + "]";
		}
	}

	public PrefetchingCapableClient(Context<Object> context) {
		super(context);
		this.context = context;
	}

	public SOAPEnvelope invoke(ServiceInvocation inv) throws IOException {
		return invoke(inv, new InvocationComparator.DefaultComparator());
	}
	public SOAPEnvelope invoke(ServiceInvocation inv, InvocationComparator comp) throws IOException {
		//Time ctxTime = (Time)context.getAttribute(Context.ATTR_TIME);
		double time = getTime();
		try {
			Boolean netAvail = (Boolean)context.getAttribute(Context.ATTR_NETWORK_AVAILABLE);
			if(netAvail != null && netAvail) {
				LOG.debug("Invocation: " + Util.toString(inv.serviceCall));
				SOAPEnvelope res = performInvocation(inv);
				AuditEvent.addEvent(time, AuditEvent.E_INV_SUCCESS);
				return res;
			}
		} catch (IOException e) {
			LOG.debug("Cannot invoke service.", e);
			throw e;
		}
		AuditEvent.addEvent(time, AuditEvent.E_INV_FAILED);

		ServiceInvocation existing = comp.matchOne(prefetchedResults.keySet(), inv);

		if(existing == null) {
			if(!prefetchedResults.isEmpty())
				LOG.debug("prefetchedResults: " + 
						Util.toString(prefetchedResults.keySet().iterator().next()));
			String msg = "Network unavailable and "
					+ "currently no prefetched result available "
					+ "for service invocation: " + xmlUtil.
						getSOAPBodyAsString((Element)inv.serviceCall);
			LOG.info(msg);
			if(LOG.isDebugEnabled()) {
				for(ServiceInvocation existInv : prefetchedResults.keySet()) {
					LOG.debug("Existing: " + xmlUtil.
							getSOAPBodyAsString((Element)existInv.serviceCall));
				}
			}
			AuditEvent.addEvent(time, AuditEvent.E_PREFETCH_MISS);
			throw new IllegalStateException(msg);
		}
		PrefetchEntry existingEntry = prefetchedResults.get(existing);
		SOAPEnvelope result = existingEntry.result;
		LOG.debug("Prefetch hit: " + Util.toString(existing.serviceCall));
		AuditEvent.addEvent(time, AuditEvent.E_PREFETCH_HIT, 
				MapBuilder.map(Context.ATTR_TIME, 
						prefetchedResults.get(existing).prefetchTime));
		existingEntry.accessTimes.add(TimeClock.now());
		if(deleteCachedResultsWhenReturned) {
			prefetchedResults.remove(existing);
		}
		cleanCacheIfNecessary(time);
		return result;
	}

	private void cleanCacheIfNecessary(double currentTime) {
		//System.out.println(lastCleanupTime + " - " + deleteCachedResultsAfter  + " -" + now);
		if(lastCleanupTime + deleteCachedResultsAfter > currentTime) {
			return;
		}
		for(ServiceInvocation i : new HashSet<>(prefetchedResults.keySet())) {
			PrefetchEntry e = prefetchedResults.get(i);
			if(e.prefetchTime < currentTime - deleteCachedResultsAfter) {
				if(e.accessTimes.isEmpty()) {
					AuditEvent.addEvent(currentTime, AuditEvent.E_UNUSED_RESULT);
				}
				LOG.debug("Cleanup client: " + e);
				prefetchedResults.remove(i);
			}
		}
		lastCleanupTime = currentTime;
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
		if(LOG.isDebugEnabled())
			LOG.debug("Got prefetch result for " + Util.toString(notification.serviceInvocation.serviceCall));
		if(notification.result != null) {
			ServiceInvocation existing = new InvocationComparator.DefaultComparator()
				.matchOne(prefetchedResults.keySet(), notification.serviceInvocation);
			PrefetchEntry entry = new PrefetchEntry();
			entry.prefetchTime = TimeClock.now();
			entry.result = notification.result;
			if(existing == null) {
				prefetchedResults.put(notification.serviceInvocation, entry);
			} else {
				PrefetchEntry e = prefetchedResults.get(existing);
				if(e.accessTimes.isEmpty()) {
					AuditEvent.addEvent(getTime(), AuditEvent.E_UNUSED_RESULT);
				}
				prefetchedResults.put(existing, entry);
			}
			//System.out.println("prefetchedResults: " + prefetchedResults);
		}
	}

	private double getTime() {
		Time ctxTime = (Time)context.getAttribute(Context.ATTR_TIME);
		if(ctxTime != null) {
			return ctxTime.time;
		}
		return TimeClock.now();
	}

	public void onContextChanged(Map<String,Object> attrs, Map<String,Object> oldAttrs) {
		LOG.trace("Context has changed. Checking if we need to prefetch.");
		for(PrefetchSubscription sub : prefetchings.values()) {
			handleRequest(sub);
		}
	}
}

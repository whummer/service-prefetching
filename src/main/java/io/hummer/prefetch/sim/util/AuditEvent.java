package io.hummer.prefetch.sim.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Simple notification service for monitoring/auditing purposes.
 * 
 * @author Waldemar Hummer
 */
public class AuditEvent {

	public static final String E_INV_SUCCESS = "inv.success";
	public static final String E_INV_FAILED = "inv.failed";
	public static final String E_PREFETCH_HIT = "prefetch.hit";
	public static final String E_PREFETCH_MISS = "prefetch.miss";

	private static final List<AuditEvent> events = new LinkedList<>();
	private static final Map<EventListener,String> listeners = new HashMap<EventListener,String>();

	public long systemTime;
	public double time;
	public String type;
	public Object data;

	public static interface EventListener {
		void notify(AuditEvent e);
	}

	public static AuditEvent addEvent(double time, String eventType) {
		return addEvent(time, eventType, null);
	}
	public static AuditEvent addEvent(double time, String eventType, Object data) {
		AuditEvent e = new AuditEvent();
		e.systemTime = System.currentTimeMillis();
		e.type = eventType;
		e.time = time;
		e.data = data;
		events.add(e);
		for(Entry<EventListener,String> entry : listeners.entrySet()) {
			if(eventType.matches(entry.getValue())) {
				entry.getKey().notify(e);
			}
		}
		return e;
	}

	public static List<AuditEvent> getEvents(String typeRegex) {
		List<AuditEvent> result = new LinkedList<AuditEvent>();
		for(AuditEvent e : events) {
			if(e.type.matches(typeRegex)) {
				result.add(e);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return "Event[" + type + ",time=" + time + "]";
	}
	public static void addListener(String typeRegex, EventListener eventListener) {
		listeners.put(eventListener, typeRegex);
	}
}

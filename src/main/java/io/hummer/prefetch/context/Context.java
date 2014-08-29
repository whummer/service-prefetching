package io.hummer.prefetch.context;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.hummer.util.coll.CollectionsUtil.MapBuilder;

/**
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class Context<T> {

	/* some attributes */

	public static final String ATTR_LOCATION = "__loc__";
	public static final String ATTR_LOCATION_LAT = "__loc_lat__";
	public static final String ATTR_LOCATION_LON = "__loc_lon__";
	public static final String ATTR_NETWORK_AVAILABLE = "__net_available__";
	public static final String ATTR_TIME = "__time__";
	public static final String ATTR_PATH = "__path__";
	public static final String ATTR_FUTURE_PATH = "__future_path__";

	/**
	 * The entries which represent the state of this context.
	 */
	private final Map<String, ContextEntry<T>> entries = new HashMap<>();
	/**
	 * List of context change listeners.
	 */
	private final List<ContextChangeListener<T>> listeners = new LinkedList<>();

	public static interface ContextChangeListener<T> {
		void onContextChanged(Map<String,T> newValue, Map<String,T> oldValue);
	}

	private static class ContextEntry<T> {
		T value;
		long expiryDate;

		public String toString() {
			return "E[" + value + "]";
		}
	}

	public boolean containsAttribute(String key) {
		return containsAttribute(key, null);
	}
	public boolean containsAttribute(String key, T value) {
		T a = getAttribute(key);
		if(a == null) return false;
		if(value == null) return true;
		return value.equals(a);
	}
	public T getAttribute(String key) {
		/* */
		cleanAttributes();

		ContextEntry<T> e = entries.get(key);
		if(e == null) return null;
		return e.value;
	}

	public void setContextAttribute(String key, T value) {
		setContextAttribute(key, value, -1);
	}

	public void setContextAttribute(String key, T value, long expiryPeriodMS) {
		Map<String,T> values = new MapBuilder<String,T>(key, value);
		setContextAttributes(values);
	}

	public void setContextAttributes(Map<String,T> values) {
		setContextAttributes(values, -1);
	}
	public void setContextAttributes(Map<String,T> values, long expiryPeriodMS) {
		Map<String,T> oldValues = new HashMap<>();
		for(String key : values.keySet()) {
			oldValues.put(key, null);
			if(entries.containsKey(key)) {
				oldValues.put(key, entries.get(key).value);
			}
			ContextEntry<T> e = new ContextEntry<>();
			T value = values.get(key);
			e.value = value;
			if(expiryPeriodMS > 0) {
				e.expiryDate = System.currentTimeMillis() + expiryPeriodMS;
			}
			entries.put(key, e);
		}
		/* notify listeners */
		for(ContextChangeListener<T> l : listeners) {
			l.onContextChanged(values, oldValues);
		}
	}

	private synchronized void cleanAttributes() {
		for(String s : new LinkedList<String>(entries.keySet())) {
			ContextEntry<T> e = entries.get(s);
			if(e.expiryDate > 0 && e.expiryDate < System.currentTimeMillis()) {
				entries.remove(s);
			}
		}
	}

	public Set<String> keySet() {
		return entries.keySet();
	}

	public void addChangeListener(ContextChangeListener<T> listener) {
		listeners.add(listener);
	}

	public boolean isNetworkUnavailable() {
		Boolean avail = (Boolean)getAttribute(ATTR_NETWORK_AVAILABLE);
		return avail != null && !avail;
	}
	public Time getTime() {
		return (Time)getAttribute(Context.ATTR_TIME);
	}

	public Context<T> copy() {
		return copy(false);
	}
	public Context<T> copy(boolean includeListeners) {
		Context<T> copy = new Context<>();
		copy.entries.putAll(entries);
		if(includeListeners) {
			copy.listeners.addAll(listeners);
		}
		return copy;
	}
	@Override
	public String toString() {
		return "Context" + entries;
	}

}

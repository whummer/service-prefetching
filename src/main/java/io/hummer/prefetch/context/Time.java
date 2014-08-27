package io.hummer.prefetch.context;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents time in a client's context.
 * @author Waldemar Hummer
 */
@XmlRootElement
public class Time implements Comparable<Time>, Serializable {
	private static final long serialVersionUID = 1L;
	@XmlElement(name="t")
	public double time;
	public int compareTo(Time o) {
		return Double.valueOf(time).compareTo(o.time);
	}
	@Override
	public int hashCode() {
		long temp = Double.doubleToLongBits(time);
		return (int) (temp ^ (temp >>> 32));
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Time other = (Time) obj;
		if (Double.doubleToLongBits(time) != Double
				.doubleToLongBits(other.time))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "T(" + time + ")";
	}
}
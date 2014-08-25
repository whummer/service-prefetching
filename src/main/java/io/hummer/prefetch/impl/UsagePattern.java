package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import com.sun.phobos.script.javascript.RhinoScriptEngineFactory;

/**
 * Defines a (typically repeated) service usage pattern.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlType
public class UsagePattern {

	@XmlAttribute(name="len")
	public Double timeLength;
	@XmlValue
	public String expression;

	private static ScriptEngine eng = new RhinoScriptEngineFactory().getScriptEngine();

	public static class ServiceUsage {
		public ServiceInvocation invocation;
		public UsagePattern pattern;
		public InvocationPredictor invocationPredictor;
	}

	/**
	 * Default c'tor, required by JAXB.
	 */
	public UsagePattern() {}
	public UsagePattern(String expression) {
		this(expression, null);
	}
	public UsagePattern(String expression, Double timeLength) {
		this.expression = expression;
		this.timeLength = timeLength;
	}

	public double predictUsage(double time) {
		if(timeLength != null) {
			time %= timeLength;
		}
		Map<String,Object> map = new HashMap<>();
		map.put("x", time);
		map.put("time", time);
		Bindings bindings = new SimpleBindings(map);
		try {
			Object d = evalJS(expression, bindings);
			return Double.parseDouble("" + d);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Object evalJS(String js, Bindings bindings) {
		try {
			return eng.eval(js, bindings);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static UsagePattern periodic(
			double repeatTimeSecs, double amount, double durationSecs) {
		return new UsagePattern(
				"x < " + (repeatTimeSecs - durationSecs) + " ? 0 : " +
				"x < " + repeatTimeSecs + " ? " + amount + " : " +
				"0", repeatTimeSecs);
	}
	public static UsagePattern constant(double amount) {
		return new UsagePattern("" + amount);
	}

	public static UsagePattern combine(final ServiceUsage ... usages) {
		UsagePattern[] patterns = new UsagePattern[usages.length];
		for(int i = 0; i < usages.length; i ++) 
			patterns[i] = usages[i].pattern;
		return combine(patterns);
	}
	public static UsagePattern combine(final UsagePattern ... patterns) {
		return new UsagePattern(null) {
			@Override
			public double predictUsage(double time) {
				double result = 0;
				for(UsagePattern p : patterns)
					result += p.predictUsage(time);
				return result;
			}
		};
	}

	public static void main(String[] args) {
		UsagePattern p1 = periodic(60, 10, 5);
		System.out.println(p1.predictUsage(123.45));
		System.out.println(p1.predictUsage(118));
		System.out.println(p1.predictUsage(56));
		UsagePattern p2 = constant(110);
		System.out.println(p2.predictUsage(123.45));
		UsagePattern p3 = combine(p1, p2);
		System.out.println(p3.predictUsage(118));
	}
}

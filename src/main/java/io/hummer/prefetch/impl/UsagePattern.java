package io.hummer.prefetch.impl;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.Time;
import io.hummer.util.coll.Pair;

import java.util.HashMap;
import java.util.List;
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

	public static class UsagePatternPredictionBased extends UsagePattern {
		public Context<Object> context;
		InvocationPredictor predictor;
		double valueIfInvocationPredicted;
		public UsagePatternPredictionBased(InvocationPredictor predictor, 
				Context<Object> ctx, double valueIfInvocationPredicted) {
			this.predictor = predictor;
			this.context = ctx;
			this.valueIfInvocationPredicted = valueIfInvocationPredicted;
		}
		public double predictUsage(double time) {
			List<Pair<Context<Object>, ServiceInvocation>> invs = 
					predictor.predictInvocations(context, new Time(time));
			if(!invs.isEmpty()) {
				return valueIfInvocationPredicted;
			}
			return 0;
		}
		public void setContext(Context<Object> context) {
			this.context = context;
		}
	}

	public static UsagePattern predictionBased(InvocationPredictor pred,
			Context<Object> ctx, double valueIfInvocationPredicted) {
		return new UsagePatternPredictionBased(pred, ctx, valueIfInvocationPredicted);
	}

	@Deprecated
	public static UsagePattern periodic(
			double repeatTimeSecs, double amount, double durationSecs) {
		return new UsagePattern(
				"x < " + (repeatTimeSecs - durationSecs) + " ? 0 : " +
				"x < " + repeatTimeSecs + " ? " + amount + " : " +
				"0", repeatTimeSecs);
	}
	@Deprecated
	public static UsagePattern constant(double amount) {
		return new UsagePattern("" + amount);
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

	private static Object evalJS(String js, Bindings bindings) {
		try {
			return eng.eval(js, bindings);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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

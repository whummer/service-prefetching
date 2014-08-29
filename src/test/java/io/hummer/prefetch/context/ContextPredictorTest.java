package io.hummer.prefetch.context;

import io.hummer.osm.util.Util;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.impl.InvocationPredictor;
import io.hummer.util.coll.Pair;
import io.hummer.util.xml.XMLUtil;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ContextPredictorTest {

	@Test
	public void test() throws Exception {
		
		Path path = new Path();
		path.add(new Path.PathPoint(new Time(18044), new Location(10,10), new NetworkQuality(false)));
		path.add(new Path.PathPoint(new Time(18054), new Location(11,11), new NetworkQuality(false)));
		path.add(new Path.PathPoint(new Time(18064), new Location(12,12), new NetworkQuality(false)));
		Context<Object> ctx = new Context<>();
		ctx.setContextAttribute(Context.ATTR_TIME, 1);
		ctx.setContextAttribute(Context.ATTR_PATH, path);
		ctx.setContextAttribute(Context.ATTR_FUTURE_PATH, path);
		ContextPredictor<Object> pred = new  ContextPredictor.
				DefaultPredictorWithUpdateInterval(60, 10);
		ServiceInvocation inv = new ServiceInvocation();
		inv.serviceCall = new XMLUtil().toElement("<foo/>");
		double timeVicinityIntoFutureSecs = 10;
		InvocationPredictor invPred = new InvocationPredictor.
				TemplateBasedInvocationPredictor(Util.toString(inv), 
						pred, timeVicinityIntoFutureSecs);
		
		List<Context<Object>> ctxPredictions = null;
		List<Pair<Context<Object>, ServiceInvocation>> predictions = null;

		for(double t = 18034.0; t < 18100; t += 10.0) {
			ctxPredictions = pred.predictContexts(ctx, 
					new Time(t), new Time(t + timeVicinityIntoFutureSecs));
			predictions = invPred.predictInvocations(ctx, 
					new Time(t));
//			System.out.println(ctxPredictions.size());
//			System.out.println(predictions.size());
		}

		double t = 18060;
		ctxPredictions = pred.predictContexts(ctx, 
				new Time(t), new Time(t + timeVicinityIntoFutureSecs));
		predictions = invPred.predictInvocations(ctx, 
				new Time(t));
//		System.out.println(ctxPredictions.size());
//		System.out.println(predictions.size());
		Assert.assertFalse(ctxPredictions.isEmpty());
		Assert.assertFalse(predictions.isEmpty());

		t = 18053;
		ctxPredictions = pred.predictContexts(ctx, 
				new Time(t), new Time(t + timeVicinityIntoFutureSecs));
		predictions = invPred.predictInvocations(ctx, 
				new Time(t));
//		System.out.println(ctxPredictions.size());
//		System.out.println(predictions.size());
		Assert.assertFalse(ctxPredictions.isEmpty());
		Assert.assertFalse(predictions.isEmpty());

//		predictions = invPred.predictInvocations(ctx, 
//				new Time(18044.0), new Time(18044.00000000001));
//		System.out.println(predictions.size());
//		predictions = invPred.predictInvocations(ctx, 
//				new Time(18054.0), new Time(18054.00000000001));
//		System.out.println(predictions.size());
//		predictions = invPred.predictInvocations(ctx, 
//				new Time(18064.0), new Time(18064.00000000001));
//		System.out.println(predictions.size());

//		predicted [18034.0,18034.00000000001]: 0
//		INFO : times UpdateInterval: T(18045.074526)-T(18045.07452600001): []
//		INFO : times UpdateInterval: T(18045.074526)-T(18045.07452600001): []
//		predicted [18045.074526,18045.07452600001]: 0
	}

}

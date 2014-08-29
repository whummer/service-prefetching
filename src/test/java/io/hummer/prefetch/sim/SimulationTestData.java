package io.hummer.prefetch.sim;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.TestConstants;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.ContextPredictor;
import io.hummer.prefetch.context.Time;
import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.prefetch.impl.InvocationPredictor;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntities;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntity;
import io.hummer.prefetch.sim.util.Util;
import io.hummer.prefetch.sim.ws.VehicleInfoService;
import io.hummer.prefetch.sim.ws.VehicleInfoService.VehicleInfoServiceImpl;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;
import io.hummer.util.log.LogUtil;
import io.hummer.util.xml.XMLUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.ws.Endpoint;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

public class SimulationTestData {

	public static final String TRACE_FILE = System.getProperty("user.home")
			+ "/Desktop/traces.txt";
	private static Map<String, List<String>> file = new HashMap<>();
	private static XMLUtil xmlUtil = new XMLUtil();
	private static final Logger LOG = LogUtil.getLogger();

	public static class ServiceUsage {
		public UsagePattern pattern;
		public InvocationPredictor invocationPredictor;

		public static UsagePattern combine(final ServiceUsage ... usages) {
			UsagePattern[] patterns = new UsagePattern[usages.length];
			for(int i = 0; i < usages.length; i ++) 
				patterns[i] = usages[i].pattern;
			return UsagePattern.combine(patterns);
		}
	}

	public static MovingEntity getData(String id) {
		List<String> lines = getLines(id);
		MovingEntity result = new MovingEntity();
		result.id = id;
		for (String s : lines) {
			String[] parts = s.split("setdest");
			if (parts.length > 1) {
				String[] coords = parts[1].trim().split(" ");
				String time = parts[0].trim().split(" ")[2];
				boolean loadDetails = false;
				result.addPathPoint(Double.parseDouble(time),
						Double.parseDouble(coords[0]),
						Double.parseDouble(coords[1]),
						Double.parseDouble(coords[2].replace("\"", "")), 
						loadDetails);
			}
		}
		return result;
	}

	private static List<String> getLines(String id) {
		if (file.isEmpty()) {
			String regex = ".*\\$node_\\(([A-Za-z0-9]+)\\).*";
			List<String> lines = Util.readFile(TRACE_FILE);
			for (String s : lines) {
				if (s.matches(regex)) {
					String i = s.replaceAll(regex, "$1");
					if (!file.containsKey(i)) {
						file.put(i, new LinkedList<String>());
					}
					file.get(i).add(s);
				}
			}
		}
		List<String> lines = file.get(id);
		if (lines != null)
			return lines;
		return Collections.emptyList();
	}

	public static MovingEntities getData(int fromCar, int toCar) {

		String file = Constants.TMP_DIR + "/traces.xml.gz";
		MovingEntities result = new MovingEntities();
		if(new File(file).exists()) {
			String content = Util.loadStringFromGzip(file);
			try {
				result = xmlUtil.toJaxbObject(MovingEntities.class, 
						xmlUtil.toElement(content));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		long t1, t2;
		double timeBetweenPointsTotal = 0;
		double totalNumPoints = 0;
		for (int i = fromCar; i <= toCar; i++) {
			String id = "" + i;
			if(!result.containsID(id)) {
				System.out.println("Retrieving data for car " + id);
				t1 = System.currentTimeMillis();
				MovingEntity ent = getData(id);
				result.entities.add(ent);
				t2 = System.currentTimeMillis();
				System.out.println("adding entity " + i + ": " + 
						ent.path.size() + " points - " + Math.abs(t2 - t1) + "ms");
				ent.getNetworkOutages();
				try {
					String xml = xmlUtil.toString(result);
					Util.storeStringGzipped(file, xml);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			MovingEntity ent = result.getEntity(id);
			/* assert that path order is correct */
			for(int j = 0; j < ent.path.size() - 1; j ++) {
				PathPoint p1 = ent.path.points.get(j);
				PathPoint p2 = ent.path.points.get(j + 1);
				double timeBetweenPoints = p2.time.time - p1.time.time;
				timeBetweenPointsTotal += timeBetweenPoints;
				if(p1.time.time > p2.time.time) {
					throw new RuntimeException("non-chronological path order");
				}
				/* if the distance between two points is too big, end the path here. */
				if(timeBetweenPoints > 60*15) {
					LOG.info("Cutting path of vehicle " + id + " at time " + 
							p1.time + ", distance: " + timeBetweenPoints);
					ent.path.points = ent.path.points.subList(0, j + 1);
				}
			}
			totalNumPoints += ent.path.size();
		}
		/* trim to number of cars */
		for(MovingEntity ent : new LinkedList<>(result.entities)) {
			if(Double.parseDouble(ent.id) < fromCar ||
					Double.parseDouble(ent.id) > toCar)
			result.entities.remove(ent);
		}
		//System.out.println(result.entities.get(0).path);
		System.out.println("Average time between time points: " + (timeBetweenPointsTotal / totalNumPoints));
		return result;
	}

	static ServiceUsage getServiceUsage1() throws Exception {
		String template = 
				"<tns:getVicinityInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<lat>{{" + Context.ATTR_LOCATION_LAT + "}}</lat>" +
				"<lon>{{" + Context.ATTR_LOCATION_LON + "}}</lon>" +
				"</tns:getVicinityInfo>";
		//UsagePattern usagePattern = UsagePattern.periodic(60, 100, 10);
		ContextPredictor<Object> ctxPredict = new ContextPredictor.DefaultPredictor();
		return constructServiceUsage(template, false, ctxPredict, 100);
	}
	static ServiceUsage getServiceUsage2() throws Exception {
		String template = 
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<lat>{{" + Context.ATTR_LOCATION_LAT + "}}</lat>" +
				"<lon>{{" + Context.ATTR_LOCATION_LON + "}}</lon>" +
				"</tns:getTrafficInfo>";
		final double updateSeconds = 20;
		double timeInterval = 10;
		//UsagePattern usagePattern = UsagePattern.periodic(60, 100, 10);
		ContextPredictor<Object> ctxPredict = new ContextPredictor.
				DefaultPredictorWithUpdateInterval(updateSeconds, timeInterval);
		return constructServiceUsage(template, false, ctxPredict, 100);
	}
	static ServiceUsage getServiceUsage3() throws Exception {
		String template = 
				"<tns:streamMedia " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<mediaID>{{" + TestConstants.ATTR_MEDIA_ID + "}}</mediaID>" +
				"<chunkID>{{" + TestConstants.ATTR_MEDIA_NEXT_CHUNK + "}}</chunkID>" +
				"</tns:streamMedia>";
		final double updateSeconds = 20;
		final double songLength = 180;
		double timeInterval = 10;
		ContextPredictor<Object> ctxPredict = new ContextPredictor.
				DefaultPredictorWithUpdateInterval(updateSeconds, timeInterval) {
			public Context<Object> predict(Context<Object> currentContext, Time t) {
				Context<Object> context = super.predict(currentContext, t);
				context.setContextAttribute(TestConstants.ATTR_MEDIA_ID, "song" + (int)(t.time / songLength));
				context.setContextAttribute(TestConstants.ATTR_MEDIA_NEXT_CHUNK, 
						"chunk" + (int)(((double)(t.time % songLength))/updateSeconds));
				return context;
			}
		};
//		UsagePattern usagePattern = UsagePattern.periodic(updateSeconds, 100, 10);
		return constructServiceUsage(template, true, ctxPredict, 100);
	}

	static ServiceUsage constructServiceUsage(String template, 
			boolean prefetchPossible, 
			//UsagePattern usagePattern,
			ContextPredictor<Object> ctxPredictor, double invocationKbps) {
		try {
			Element body = WSClient.toElement(template);
			ServiceInvocation tmp = new ServiceInvocation();
			tmp.serviceCall = WSClient.createEnvelopeFromBody(body);
//			tmp.prefetchPossible = prefetchPossible;
			tmp.serviceEPR = eprTrafficService;
			double stepSize = 10;
			InvocationPredictor invPred = new InvocationPredictor.
					TemplateBasedInvocationPredictor(xmlUtil.toString(tmp), 
							ctxPredictor, stepSize);
			UsagePattern usagePattern = UsagePattern.predictionBased(invPred, null, invocationKbps);
			
//			ServiceInvocationBuilder b = new ServiceInvocationBuilder.
//					TemplateBasedInvocationBuilder(xmlUtil.toString(tmp));
//			b.prefetchPossible = tmp.prefetchPossible;
//			b.serviceEPR = tmp.serviceEPR;
			ServiceUsage use = new ServiceUsage();
//			use.invocation = b;
			use.pattern = usagePattern;
			use.invocationPredictor = invPred;
			return use;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String urlTrafficService;
	private static W3CEndpointReference eprTrafficService;
	public static void setServiceURL(String serviceURL) {
		urlTrafficService = serviceURL;
		eprTrafficService = W3CEndpointReferenceUtils.
				createEndpointReference(urlTrafficService);
	}
	static void deployTestServices(String url) throws Exception {
		setServiceURL(url);
		VehicleInfoService s = new VehicleInfoServiceImpl();
		Endpoint.publish(urlTrafficService, s);
		WSClient.cachedResponseObject = WSClient.createEnvelopeFromBody(
				new XMLUtil().toElement("<result/>"));
	}

//	static void addUsagePatterns(MovingEntities entities) throws Exception {
//
//		ServiceInvocation inv1 = new ServiceInvocation();
//		ServiceInvocation inv2 = new ServiceInvocation();
//		inv1.serviceEPR = eprTrafficService;
//		inv2.serviceEPR = eprTrafficService;
//		Element body = WSClient.toElement(
//				"<tns:getTrafficInfo " +
//				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\"/>");
//		inv1.serviceCall = WSClient.createEnvelopeFromBody(body);
//		inv2.serviceCall = WSClient.createEnvelopeFromBody(body);
//		for(MovingEntity ent : entities.entities) {
//			if(ent.usagePatterns == null) {
//				ent.usagePatterns = new LinkedList<>();
//			}
//			ent.usagePatterns.add(new ServiceUsagePattern(inv1, UsagePattern.periodic(60, 50, 4)));
//			ent.usagePatterns.add(new ServiceUsagePattern(inv2, UsagePattern.constant(20)));
//		}
//	}

}

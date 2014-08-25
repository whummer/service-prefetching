package io.hummer.prefetch.sim;

import io.hummer.prefetch.TestConstants;
import io.hummer.prefetch.VehicleInfoService;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.VehicleInfoService.VehicleInfoServiceImpl;
import io.hummer.prefetch.client.ServiceInvocationBuilder;
import io.hummer.prefetch.impl.Context;
import io.hummer.prefetch.impl.InvocationPredictor;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.impl.UsagePattern.ServiceUsage;
import io.hummer.prefetch.sim.Constants;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntities;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntity;
import io.hummer.prefetch.sim.VehicleSimulation.ServiceUsagePattern;
import io.hummer.prefetch.sim.util.Util;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.ws.Endpoint;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.w3c.dom.Element;

public class SimulationTestData {

	public static final String TRACE_FILE = System.getProperty("user.home")
			+ "/Desktop/traces.txt";
	// private static List<String> file = new LinkedList<>();
	private static Map<String, List<String>> file = new HashMap<>();

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

	public static MovingEntities getData() {
		int numCars = 50;

		String file = Constants.TMP_DIR + "/traces.xml.gz";
		MovingEntities result = new MovingEntities();
		if(new File(file).exists()) {
			String content = Util.loadGzippedObject(file);
			try {
				result = Util.toJaxbObject(MovingEntities.class, 
						Util.toElement(content));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		long t1, t2;
		double timeBetweenPoints = 0;
		double totalNumPoints = 0;
		for (int i = 1; i <= numCars; i++) {
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
				String xml = Util.toString(result);
				Util.storeObjectGzipped(file, xml);
			}
			MovingEntity ent = result.getEntity(id);
			/* assert that path order is correct */
			for(int j = 0; j < ent.path.size() - 1; j ++) {
				timeBetweenPoints += ent.path.get(j + 1).time.time - ent.path.get(j).time.time;
				if(ent.path.get(j).time.time > ent.path.get(j + 1).time.time) {
					throw new RuntimeException("non-chronological path order");
				}
			}
			totalNumPoints += ent.path.size();
		}
		/* trim to number of cars */
		while(result.entities.size() > numCars) {
			result.entities.remove(result.entities.size() - 1);
		}
		//System.out.println(result.entities.get(0).path);
		System.out.println("Average time between time points: " + (timeBetweenPoints / totalNumPoints));
		return result;
	}

	static ServiceUsage getServiceUsage1() throws Exception {
		Element body = WSClient.toElement(
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<lat>{{" + Context.ATTR_LOCATION_LAT + "}}</lat>" +
				"<lon>{{" + Context.ATTR_LOCATION_LON + "}}</lon>" +
				"</tns:getTrafficInfo>");
		UsagePattern usagePattern = UsagePattern.periodic(60, 100, 10);
		InvocationPredictor invPred = null;
		return constructServiceUsage(body, false, usagePattern, invPred);
	}
	static ServiceUsage getServiceUsage2() throws Exception {
		String template = 
				"<tns:streamMedia " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<mediaID>{{" + TestConstants.ATTR_MEDIA_ID + "}}</mediaID>" +
				"<chunkID>{{" + TestConstants.ATTR_MEDIA_NEXT_CHUNK + "}}</chunkID>" +
				"</tns:streamMedia>";
		Element body = WSClient.toElement(template);
		UsagePattern usagePattern = UsagePattern.constant(150);
		InvocationPredictor invPred = null;
		return constructServiceUsage(body, true, usagePattern, invPred);
	}

	static ServiceUsage constructServiceUsage(Element body, 
			boolean prefetchPossible, UsagePattern usagePattern,
			InvocationPredictor invocationPredictor) {
		try {
			ServiceInvocation tmp = new ServiceInvocation();
			tmp.serviceCall = WSClient.createEnvelopeFromBody(body);
			tmp.prefetchPossible = prefetchPossible;
			tmp.serviceEPR = eprTrafficService;
			ServiceInvocationBuilder b = new ServiceInvocationBuilder.
					TemplateBasedInvocationBuilder(Util.toString(tmp));
			b.prefetchPossible = tmp.prefetchPossible;
			b.serviceEPR = tmp.serviceEPR;
			ServiceUsage use = new ServiceUsage();
			use.invocation = b;
			use.pattern = usagePattern;
			use.invocationPredictor = invocationPredictor;
			return use;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void addUsagePatterns(MovingEntities entities) throws Exception {

		ServiceInvocation inv1 = new ServiceInvocation();
		ServiceInvocation inv2 = new ServiceInvocation();
		inv1.serviceEPR = eprTrafficService;
		inv2.serviceEPR = eprTrafficService;
		Element body = WSClient.toElement(
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\"/>");
		inv1.serviceCall = WSClient.createEnvelopeFromBody(body);
		inv2.serviceCall = WSClient.createEnvelopeFromBody(body);
		for(MovingEntity ent : entities.entities) {
			if(ent.usagePatterns == null) {
				ent.usagePatterns = new LinkedList<>();
			}
			ent.usagePatterns.add(new ServiceUsagePattern(inv1, UsagePattern.periodic(60, 50, 4)));
			ent.usagePatterns.add(new ServiceUsagePattern(inv2, UsagePattern.constant(20)));
		}
	}
	

	private static String urlTrafficService = 
			"http://localhost:8283/traffic";
	private static W3CEndpointReference eprTrafficService = 
			W3CEndpointReferenceUtils.createEndpointReference(
					urlTrafficService);
	static void deployTestServices() {
		VehicleInfoService s = new VehicleInfoServiceImpl();
		Endpoint.publish(urlTrafficService, s);
	}

}

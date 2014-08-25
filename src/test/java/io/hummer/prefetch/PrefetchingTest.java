package io.hummer.prefetch;

import static org.junit.Assert.assertTrue;
import io.hummer.prefetch.PrefetchingService.PrefetchRequest;
import io.hummer.prefetch.PrefetchingService.PrefetchResponse;
import io.hummer.prefetch.VehicleInfoService.VehicleInfoServiceImpl;
import io.hummer.prefetch.client.NotificationReceiverService;
import io.hummer.prefetch.impl.Context;
import io.hummer.prefetch.impl.PrefetchingServiceImpl;
import io.hummer.prefetch.strategy.PrefetchStrategyNone;
import io.hummer.prefetch.strategy.PrefetchStrategyPeriodic;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;

import java.net.URL;

import javax.xml.ws.Endpoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

/**
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class PrefetchingTest {

	String urlPrefetch = "http://localhost:8283/prefetch";
	Endpoint endpointPrefetch;

	@Before
	public void setUp() {
		Context<Object> ctx = new Context<Object>();
		PrefetchingServiceImpl s = new PrefetchingServiceImpl(ctx);
		endpointPrefetch = Endpoint.publish(urlPrefetch, s);
	}
	
	@After
	public void tearDown() {
		//endpointPrefetch.stop(); // TODO
	}

	@Test
	public void testPrefetching() throws Exception {
		VehicleInfoService s = new VehicleInfoServiceImpl();
		String urlTraffic = "http://localhost:8283/traffic";
		Endpoint.publish(urlTraffic, s);
		NotificationReceiverService n = new NotificationReceiverService();
		String urlNotify = "http://localhost:8283/notify";
		n.deploy(urlNotify);

		/* register subscription */
		PrefetchingService prefetch = WSClient.createClientJaxws(
				PrefetchingService.class,
				new URL(urlPrefetch + "?wsdl"));
		PrefetchRequest request = new PrefetchRequest();
		request.invocation.serviceEPR = W3CEndpointReferenceUtils.createEndpointReference(urlTraffic);
		request.notifyRemote = W3CEndpointReferenceUtils.createEndpointReference(urlNotify);
		Element body = WSClient.toElement(
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\"/>");
		request.invocation.serviceCall = WSClient.createEnvelopeFromBody(body);
		request.strategy = new PrefetchStrategyPeriodic(1000);
		PrefetchResponse response = prefetch.setPrefetchingStrategy(request);

		Thread.sleep(2000);

		/* check */
		int size1 = n.notifications.size();
		assertTrue(size1 >= 1);
		Thread.sleep(2000);
		int size2 = n.notifications.size();
		assertTrue(size2 > size1);

		/* cancel subscription */
		PrefetchRequest request1 = new PrefetchRequest();
		request1.subscriptionID = response.subscriptionID;
		request1.strategy = new PrefetchStrategyNone();
		prefetch.setPrefetchingStrategy(request1);

		/* check */
		Thread.sleep(1000);
		int size3 = n.notifications.size();
		Thread.sleep(2000);
		int size4 = n.notifications.size();
		assertTrue(size3 == size4);
	}
	
}
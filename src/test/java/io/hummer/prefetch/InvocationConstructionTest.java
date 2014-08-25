package io.hummer.prefetch;

import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.impl.Context;
import io.hummer.prefetch.sim.util.Util;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;

import org.junit.Test;

import static org.junit.Assert.*;

import org.w3c.dom.Element;

public class InvocationConstructionTest {

	@Test
	public void construct() throws Exception {
		ServiceInvocation tmp = new ServiceInvocation();
		Element body = WSClient.toElement(
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<lat>{{" + Context.ATTR_LOCATION_LAT + "}}</lat>" +
				"<lon>{{" + Context.ATTR_LOCATION_LON + "}}</lon>" +
				"</tns:getTrafficInfo>");
		tmp.serviceCall = WSClient.createEnvelopeFromBody(body);
		tmp.prefetchPossible = true;
		tmp.serviceEPR = W3CEndpointReferenceUtils.createEndpointReference("http://foo");
		final String tmpl = Util.toString(tmp);
		assertTrue(tmpl.contains("getTrafficInfo"));
	}

}

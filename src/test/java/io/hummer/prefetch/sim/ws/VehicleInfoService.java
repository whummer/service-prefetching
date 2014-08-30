package io.hummer.prefetch.sim.ws;

import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.xml.bind.annotation.XmlRootElement;

@WebService(targetNamespace = VehicleInfoService.NAMESPACE)
public interface VehicleInfoService {

	public static final String NAMESPACE = "http://simpli-city.eu/prefetch";

	@WebService(endpointInterface = "io.hummer.prefetch.sim.ws.VehicleInfoService")
	public static class VehicleInfoServiceImpl implements VehicleInfoService {
		public GetTrafficInfoResponse getTrafficInfo(GetTrafficInfo request) {
			return new GetTrafficInfoResponse();
		}
		public StreamMediaResponse streamMedia(StreamMedia request) {
			return new StreamMediaResponse();
		}
		public GetVicinityInfoResponse getVicinityInfo(GetVicinityInfo request) {
			return new GetVicinityInfoResponse();
		}
		public RerouteResponse reroute(Reroute request) {
			return new RerouteResponse();
		}
		public GetMailResponse getMail(GetMail request) {
			return new GetMailResponse();
		}
		public SyncUpdatesResponse syncUpdates(SyncUpdates request) {
			return new SyncUpdatesResponse();
		}
	}

	/* get traffic info */
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetTrafficInfo {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetTrafficInfoResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	GetTrafficInfoResponse getTrafficInfo(GetTrafficInfo request);

	/* media streaming */
	@XmlRootElement(namespace = NAMESPACE)
	public static class StreamMedia {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class StreamMediaResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	StreamMediaResponse streamMedia(StreamMedia request);

	/* vicinity info */
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetVicinityInfo {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetVicinityInfoResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	GetVicinityInfoResponse getVicinityInfo(GetVicinityInfo request);

	/* routing */
	@XmlRootElement(namespace = NAMESPACE)
	public static class Reroute {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class RerouteResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	RerouteResponse reroute(Reroute request);

	/* mail */
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetMail {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class GetMailResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	GetMailResponse getMail(GetMail request);

	/* sync & updates */
	@XmlRootElement(namespace = NAMESPACE)
	public static class SyncUpdates {
	}
	@XmlRootElement(namespace = NAMESPACE)
	public static class SyncUpdatesResponse {
	}
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	SyncUpdatesResponse syncUpdates(SyncUpdates request);
}

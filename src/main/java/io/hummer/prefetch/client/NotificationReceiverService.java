package io.hummer.prefetch.client;

import io.hummer.prefetch.PrefetchingService;
import io.hummer.prefetch.PrefetchingService.PrefetchNotification;

import java.util.concurrent.LinkedBlockingQueue;

import javax.jws.WebService;
import javax.xml.ws.Endpoint;

/**
 * Simple implementation of the prefetching notification service.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@WebService(endpointInterface = "eu.simpli_city.ctx_personalize.interfaces.PrefetchingService$PrefetchingResultReceiver")
public class NotificationReceiverService implements PrefetchingService.PrefetchingResultReceiver {

	public static final String NAMESPACE = "http://simpli-city.eu/notify";

	public final LinkedBlockingQueue<PrefetchNotification> notifications = 
			new LinkedBlockingQueue<PrefetchingService.PrefetchNotification>();

	public void notify(PrefetchNotification notification) {
		try {
			notifications.put(notification);
		} catch (InterruptedException e) {
			notifications.add(notification);
		}
	}

	public Endpoint deploy(int port) {
		return deploy("http://0.0.0.0:" + port + "/notify");
	}
	public Endpoint deploy(String urlNotify) {
		return Endpoint.publish(urlNotify, this);
	}
}

package fr.wseduc.webutils.eventbus;

import fr.wseduc.vertx.eventbus.EventBusWrapperFactory;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;

public class EventBusWithLoggerFactory implements EventBusWrapperFactory{

	@Override
	public EventBus getEventBus(Vertx vertx) {
		return new EventBusWithLogger(vertx.eventBus());
	}

}

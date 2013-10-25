package edu.one.core.infra;

import java.io.IOException;
import java.util.List;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import edu.one.core.infra.http.Renders;
import edu.one.core.infra.security.resources.UserInfos;

public class NotificationHelper {

	private static final String TIMELINE_ADDRESS = "wse.timeline";
	private static final String EMAIL_ADDRESS = "wse.email";
	private final EventBus eb;
	private final Renders render;

	public NotificationHelper(EventBus eb, Container container) {
		this.eb = eb;
		this.render = new Renders(container);
	}

	public void notifyTimeline(HttpServerRequest request, UserInfos sender,
							   List<String> recipients, String resource, String template, JsonObject params)
			throws IOException {
		notifyTimeline(request, sender, recipients, resource, null, template, params);
	}

	public void notifyTimeline(HttpServerRequest request, UserInfos sender,
			List<String> recipients, String resource, String subResource, String template, JsonObject params)
					throws IOException {
		JsonObject event = new JsonObject()
		.putString("action", "add")
		.putString("resource", resource)
		.putString("sender", sender.getUserId())
		.putString("message", render.processTemplate(request, template, params))
		.putArray("recipients", new JsonArray(recipients.toArray()));
		if (subResource != null && !subResource.trim().isEmpty()) {
			event.putString("sub-resource", subResource);
		}
		eb.send(TIMELINE_ADDRESS, event);
	}

	public void deleteFromTimeline(String resource) {
		JsonObject json = new JsonObject()
		.putString("action", "delete")
		.putString("resource", resource);
		eb.send(TIMELINE_ADDRESS, json);
	}

	public void deleteSubResourceFromTimeline(String subResource) {
		JsonObject json = new JsonObject()
				.putString("action", "deleteSubResource")
				.putString("sub-resource", subResource);
		eb.send(TIMELINE_ADDRESS, json);
	}

	public void sendEmail(HttpServerRequest request, String to, String from, String cc, String bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, Handler<Message<JsonObject>> handler) throws IOException {
		JsonObject json = new JsonObject()
		.putString("to", to)
		.putString("from", from)
		.putString("cc", cc)
		.putString("bcc", bcc);
		if (translateSubject) {
			json.putString("subject", I18n.getInstance().translate(
					subject, request.headers().get("Accept-Language")));
		} else {
			json.putString("subject", subject);
		}
		String body = render.processTemplate(request, templateBody, templateParams);
		json.putString("body", new String(body.getBytes("UTF-8"), "ISO-8859-1"));
		eb.send(EMAIL_ADDRESS, json, handler);
	}

}

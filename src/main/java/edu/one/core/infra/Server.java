package edu.one.core.infra;

import java.io.IOException;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentMap;

import com.wse.eventbus.EventBusWrapperFactory;
import edu.one.core.infra.eventbus.EventBusWithLogger;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import edu.one.core.infra.http.StaticResource;
import edu.one.core.infra.request.CookieHelper;
import edu.one.core.infra.security.SecuredAction;

public abstract class Server extends Verticle {

	public Logger log;
	public JsonObject config;
	public RouteMatcher rm;
	public TracerHelper trace;
	private I18n i18n;
	protected Map<String, SecuredAction> securedActions;
	private ConcurrentMap<String, String> staticRessources;
	private boolean dev;

	@Override
	public void start() {
		super.start();
		log = container.logger();
		if (config == null) {
			config = container.config();
		}
		rm = new RouteMatcher();
		trace = new TracerHelper(Server.getEventBus(vertx), "log.address", this.getClass().getSimpleName());
		i18n = I18n.getInstance();
		i18n.init(container, vertx);
		CookieHelper.getInstance().init((String) vertx
				.sharedData().getMap("server").get("signKey"), log);
		staticRessources = vertx.sharedData().getMap("staticRessources");
		dev = "dev".equals(config.getString("mode"));

		log.info("Verticle: " + this.getClass().getSimpleName() + " starts on port: " + config.getInteger("port"));

		final String prefix = getPathPrefix(config);
		// Serve public static resource like img, css, js. By convention in /public directory
		// Dummy impl
		rm.getWithRegEx(prefix.replaceAll("\\/", "\\/") + "\\/public\\/.+",
				new Handler<HttpServerRequest>() {
			public void handle(final HttpServerRequest request) {
				if (dev) {
					request.response().sendFile("." + request.path().substring(prefix.length()));
				} else {
					if (staticRessources.containsKey(request.uri())) {
						StaticResource.serveRessource(request,
								"." + request.path().substring(prefix.length()),
								staticRessources.get(request.uri()));
					} else {
						vertx.fileSystem().props("." + request.path().substring(prefix.length()),
								new Handler<AsyncResult<FileProps>>(){
							@Override
							public void handle(AsyncResult<FileProps> af) {
								if (af.succeeded()) {
									String lastModified = StaticResource.formatDate(af.result().lastModifiedTime());
									staticRessources.put(request.uri(), lastModified);
									StaticResource.serveRessource(request,
											"." + request.path().substring(prefix.length()),
											lastModified);
								} else {
									request.response().sendFile("." + request.path().substring(prefix.length()));
								}
							}
						});
					}
				}
			}
		});

		rm.get(prefix + "/i18n", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				Controller.renderJson(request, i18n.load(request.headers().get("Accept-Language")));
			}
		});

		rm.get(prefix + "/monitoring", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest event) {
				Controller.renderJson(event, new JsonObject().putString("test", "ok"));
			}
		});

		try {
			JsonObject application = new JsonObject()
			.putString("name", config.getString("app-name", this.getClass().getSimpleName()))
			.putString("icon", config.getString("app-icon", ""))
			.putString("address", config.getString("app-address", ""));
			JsonArray actions = StartupUtils.loadSecuredActions();
			securedActions = StartupUtils.securedActionsToMap(actions);
			StartupUtils.sendStartup(application, actions,
					Server.getEventBus(vertx), config.getString("app-registry.address", "wse.app.registry"));
		} catch (IOException e) {
			log.error("Error application not registred.", e);
		}
		vertx.createHttpServer().requestHandler(rm).listen(config.getInteger("port"));
	}


	/**
	 * @deprecated Use request.formAttributes() instead
	 * @param request http request
	 * @param handler receive attributes
	 */
	public void bodyToParams(final HttpServerRequest request, final Handler<MultiMap> handler) {
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				handler.handle(request.formAttributes());
			}
		});
	}

	public static String getPathPrefix(JsonObject config) {
		String path = config.getString("path-prefix");
		if (path == null) {
			String verticle = config.getString("main");
			if (verticle != null && !verticle.trim().isEmpty() && verticle.contains(".")) {
				path = verticle.substring(verticle.lastIndexOf('.') + 1).toLowerCase();
			}
		}
		if ("".equals(path) || "/".equals(path)) {
			return "";
		}
		return "/" + path;
	}

	public static EventBus getEventBus(Vertx vertx) {
		ServiceLoader<EventBusWrapperFactory> factory = ServiceLoader
				.load(EventBusWrapperFactory.class);
		if (factory.iterator().hasNext()) {
			return factory.iterator().next().getEventBus(vertx);
		}
		return vertx.eventBus();
	}

}

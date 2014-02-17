package fr.wseduc.webutils;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Utils {

	public static <T> T getOrElse(T value, T defaultValue) {
		return getOrElse(value, defaultValue, true);
	}

	public static <T> T getOrElse(T value, T defaultValue, boolean allowEmpty) {
		if (value != null && (allowEmpty || !value.toString().trim().isEmpty())) {
			return value;
		}
		return defaultValue;
	}

	public static String inputStreamToString(InputStream in) {
		Scanner scanner = new Scanner(in, "UTF-8");
		String content = scanner.useDelimiter("\\A").next();
		scanner.close();
		return content;
	}

	public static JsonObject validAndGet(JsonObject json, List<String> fields,
				List<String> requiredFields) {
		if (json != null) {
			JsonObject e = json.copy();
			for (String attr: json.getFieldNames()) {
				if (!fields.contains(attr) || e.getValue(attr) == null) {
					e.removeField(attr);
				}
			}
			if (e.toMap().keySet().containsAll(requiredFields)) {
				return e;
			}
		}
		return null;
	}

	public static Either<String, JsonObject> validResult(Message<JsonObject> res) {
		if ("ok".equals(res.body().getString("status"))) {
			JsonObject r = res.body().getObject("result");
			if (r == null) {
				r = res.body();
				r.removeField("status");
			}
			return new Either.Right<>(r);
		} else {
			return new Either.Left<>(res.body().getString("message", ""));
		}
	}

	public static Either<String, JsonArray> validResults(Message<JsonObject> res) {
		if ("ok".equals(res.body().getString("status"))) {
			return new Either.Right<>(res.body().getArray("results", new JsonArray()));
		} else {
			return new Either.Left<>(res.body().getString("message", ""));
		}
	}

	// TODO improve with type and validation
	public static JsonObject jsonFromMultimap(MultiMap attributes) {
		JsonObject json = new JsonObject();
		if (attributes != null) {
			for (Map.Entry<String, String> e: attributes.entries()) {
				json.putString(e.getKey(), e.getValue());
			}
		}
		return json;
	}

	public static <T> boolean defaultValidationError(JsonObject c,
			Handler<Either<String, T>> result, String ... params) {
		return validationError(c, result, "invalid.field", params);
	}

	public static <T> boolean validationError(JsonObject c,
			Handler<Either<String, T>> result, String errorMessage, String ... params) {
		if (c == null) {
			result.handle(new Either.Left<String, T>(errorMessage));
			return true;
		}
		return validationParamsError(result, errorMessage, params);
	}

	public static <T> boolean defaultValidationParamsError(Handler<Either<String, T>> result, String ... params) {
		return validationParamsError(result, "invalid.parameter", params);
	}

	public static <T> boolean validationParamsError(Handler<Either<String, T>> result,
			String errorMessage, String ... params) {
		if (params.length > 0) {
			for (String s : params) {
				if (s == null) {
					result.handle(new Either.Left<String, T>(errorMessage));
					return true;
				}
			}
		}
		return false;
	}

	public static <T extends Enum<T>> T stringToEnum(String name, T defaultValue, Class<T> type) {
		if (name != null) {
			try {
				return Enum.valueOf(type, name);
			} catch (IllegalArgumentException | NullPointerException e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}

}

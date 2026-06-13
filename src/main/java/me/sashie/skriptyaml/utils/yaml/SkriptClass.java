package me.sashie.skriptyaml.utils.yaml;

import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.SerializedVariable;

import java.lang.reflect.Method;
import java.util.Base64;

public class SkriptClass {

	private static final String TEXT_COMPONENT_TYPE = "textcomponent";
	private static final String MINIMESSAGE_FORMAT = "minimessage";
	private static final String ADVENTURE_COMPONENT_CLASS = "net.kyori.adventure.text.Component";
	private static final String MINIMESSAGE_CLASS = "net.kyori.adventure.text.minimessage.MiniMessage";

	private final String type;
	private final String format;
	private final String data;

	public SkriptClass(String type, byte[] data) {
		this.type = type;
		this.format = null;
		this.data = Base64.getEncoder().encodeToString(data);
	}

	public SkriptClass(Object value, String type, byte[] data) {
		this.type = type;

		String readableData = serializeReadableData(value, type);
		if (readableData != null) {
			this.format = MINIMESSAGE_FORMAT;
			this.data = readableData;
		} else {
			this.format = null;
			this.data = Base64.getEncoder().encodeToString(data);
		}
	}

	public SkriptClass(Object value) {
		SerializedVariable.Value val = Classes.serialize(value);
		this.type = val.type;

		String readableData = serializeReadableData(value, val.type);
		if (readableData != null) {
			this.format = MINIMESSAGE_FORMAT;
			this.data = readableData;
		} else {
			this.format = null;
			this.data = Base64.getEncoder().encodeToString(val.data);
		}
	}

	public String getType() {
		return this.type;
	}

	public String getFormat() {
		return this.format;
	}

	public String getData() {
		return this.data;
	}

	public Object deserialize() {
		return SkriptClass.deserialize(this.type, this.data, this.format);
	}

	public static Object deserialize(String type, String data) {
		return deserialize(type, data, null);
	}

	public static Object deserialize(String type, String data, String format) {
		Object readableData = deserializeReadableData(data, format);
		if (readableData != null)
			return readableData;

		return Classes.deserialize(type, Base64.getDecoder().decode(data));
	}

	private static String serializeReadableData(Object value, String type) {
		if (value == null || (!isTextComponent(type) && !isAdventureComponent(value)))
			return null;

		try {
			Class<?> componentClass = Class.forName(ADVENTURE_COMPONENT_CLASS);
			if (!componentClass.isInstance(value))
				return null;

			Class<?> miniMessageClass = Class.forName(MINIMESSAGE_CLASS);
			Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
			Method serialize = miniMessageClass.getMethod("serialize", componentClass);
			return (String) serialize.invoke(miniMessage, value);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
			return null;
		}
	}

	private static Object deserializeReadableData(String data, String format) {
		if (!MINIMESSAGE_FORMAT.equalsIgnoreCase(format))
			return null;

		try {
			Class<?> miniMessageClass = Class.forName(MINIMESSAGE_CLASS);
			Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
			Method deserialize = miniMessageClass.getMethod("deserialize", String.class);
			return deserialize.invoke(miniMessage, data);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
			return data;
		}
	}

	private static boolean isTextComponent(String type) {
		return type != null && TEXT_COMPONENT_TYPE.equalsIgnoreCase(type);
	}

	private static boolean isAdventureComponent(Object value) {
		try {
			return Class.forName(ADVENTURE_COMPONENT_CLASS).isInstance(value);
		} catch (ClassNotFoundException | LinkageError ex) {
			return false;
		}
	}
}

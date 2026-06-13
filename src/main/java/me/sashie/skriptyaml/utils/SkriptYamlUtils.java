package me.sashie.skriptyaml.utils;

import me.sashie.skriptyaml.SkriptYaml;
import me.sashie.skriptyaml.debug.SkriptNode;
import me.sashie.skriptyaml.utils.yaml.YAMLProcessor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class SkriptYamlUtils {

	public static YAMLProcessor yamlExists(String name, SkriptNode skriptNode) {
		YAMLProcessor yaml = SkriptYaml.YAML_STORE.get(name);
		if (yaml != null)
			return yaml;
		SkriptYaml.warn("No yaml by the name '" + name + "' has been loaded " + skriptNode.toString());
		return null;
	}

	public static String getServerPath() {
		return new File("").getAbsoluteFile().getAbsolutePath() + File.separator;
	}

	public static File getFile(String file, boolean isNonRelative) {
		if (isNonRelative) {
			return new File(StringUtil.checkRoot(StringUtil.checkSeparator(file)));
		} else {
			String server = getServerPath();
			return new File(server + StringUtil.checkSeparator(file));
		}
	}

	/**
	 * Resolves a file case-insensitively. If the exact file does not exist but a sibling with the same
	 * name in a different case does, that sibling is returned. This lets {@code load yaml "y.yml"} pick up
	 * an on-disk {@code Y.yml} on case-sensitive filesystems (Linux) instead of silently creating a new
	 * empty file. The exact-match fast path keeps the common case cheap (no directory listing).
	 *
	 * @param file the file as typed/resolved from the script
	 * @return the matching on-disk file, or the original file if no case-variant exists
	 */
	public static File resolveIgnoreCase(File file) {
		if (file.exists())
			return file;

		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory())
			return file;

		String target = file.getName();
		File[] siblings = parent.listFiles();
		if (siblings == null)
			return file;

		File match = null;
		int matches = 0;
		for (File sibling : siblings) {
			if (!sibling.isFile() || !sibling.getName().equalsIgnoreCase(target))
				continue;
			matches++;
			// pick deterministically (lowest name) so behaviour is stable regardless of OS listing order
			if (match == null || sibling.getName().compareTo(match.getName()) < 0)
				match = sibling;
		}

		if (matches > 1)
			SkriptYaml.warn("[Load Yaml] " + matches + " case-variant files match '" + target + "' in '" + parent.getPath() + "', using '" + match.getName() + "'");

		return match != null ? match : file;
	}

	public static File[] directoryFilter(String name, boolean root, String errorPrefix, SkriptNode skriptNode) {
		File dir = null;

		if (root) {
			dir = new File(StringUtil.checkRoot(name));
		} else {
			String server = getServerPath();
			dir = new File(server + File.separator + name);
		}

		if(!dir.exists()) {
			SkriptYaml.warn("[" + errorPrefix + " Yaml] " + name + " does not exist! " + skriptNode.toString());
			return null;
		}

		if(!dir.isDirectory()) {
			SkriptYaml.warn("[" + errorPrefix + " Yaml] " + name + " is not a directory! " + skriptNode.toString());
			return null;
		}

		return dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				if (filename.endsWith(".yml") | filename.endsWith(".yaml"))
					return true;
				return false;
			}
		});
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getType(Class<T> c) {
		return (Class<T>) ((ParameterizedType) c.getGenericSuperclass()).getActualTypeArguments()[0];
	}

	@SuppressWarnings("unchecked")
	public final static <T> T[] convertToArray(Object original, Class<T> to) throws ClassCastException {
		T[] end = (T[]) Array.newInstance(to, 1);
		T converted = SkriptYaml.getInstance().getSkriptAdapter().convert(original, to);
		if (converted != null) {
			end[0] = converted;
		} else {
			throw new ClassCastException();
		}
		return end;
	}

	private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	public static Object convertUUIDs(Object input) {
		if (input instanceof String) {
			if (UUID_PATTERN.matcher((String) input).matches()) {
				input = UUID.fromString((String) input);
			}
		}
		return input;
	}

	/**
	 * Some yaml files store locations as plain key/value maps (world, x, y, z and optionally yaw/pitch)
	 * instead of using skript-yaml's tagged {@code !location} format or Bukkit's serialized
	 * ({@code ==: org.bukkit.Location}) format. When such a map is requested as a value, Skript still
	 * expects an actual {@link Location} (e.g. for teleports), which previously caused a
	 * {@code LinkedHashMap cannot be cast to Location} error.
	 * <p>
	 * This checks whether the given object is a location-shaped map and, if so, builds a real
	 * {@link Location} from it. Anything that is not a location-shaped map is returned unchanged.
	 *
	 * @param input the raw value read from the yaml
	 * @return a {@link Location} if the input was a location-shaped map, otherwise the original input
	 */
	public static Object tryConvertLocation(Object input) {
		if (!(input instanceof Map))
			return input;

		Map<?, ?> map = (Map<?, ?>) input;

		// already a Bukkit-serialized object, let the normal deserialization handle it
		if (map.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY))
			return input;

		Object worldName = map.get("world");
		if (!(worldName instanceof String))
			return input;

		Double x = castDouble(map.get("x"));
		Double y = castDouble(map.get("y"));
		Double z = castDouble(map.get("z"));
		if (x == null || y == null || z == null)
			return input;

		World world = Bukkit.getServer().getWorld((String) worldName);
		if (world == null) // world not loaded, leave the raw map untouched
			return input;

		Double yaw = castDouble(map.get("yaw"));
		Double pitch = castDouble(map.get("pitch"));

		return new Location(world, x, y, z,
				yaw == null ? 0f : yaw.floatValue(),
				pitch == null ? 0f : pitch.floatValue());
	}

	private static Double castDouble(Object o) {
		if (o instanceof Number)
			return ((Number) o).doubleValue();
		if (o instanceof String) {
			try {
				return Double.parseDouble((String) o);
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}
}

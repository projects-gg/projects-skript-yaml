package me.sashie.skriptyaml;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.SkriptAddon;
import me.sashie.skriptyaml.api.ConstructedClass;
import me.sashie.skriptyaml.api.RepresentedClass;
import me.sashie.skriptyaml.utils.SkriptYamlUtils;
import me.sashie.skriptyaml.utils.UpdateChecker;
import me.sashie.skriptyaml.utils.versions.*;
import me.sashie.skriptyaml.utils.yaml.SkriptYamlConstructor;
import me.sashie.skriptyaml.utils.yaml.SkriptYamlRepresenter;
import me.sashie.skriptyaml.utils.yaml.YAMLProcessor;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;

public class SkriptYaml extends JavaPlugin {

	public final static Logger LOGGER = Bukkit.getServer() != null ? Bukkit.getLogger() : Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	// Case-insensitive ID store: a yaml loaded `as "Y"` is found by set/save/get using "y" (and vice versa).
	// ConcurrentSkipListMap keeps the thread-safety the async effects rely on and preserves the first-inserted
	// key casing. String.CASE_INSENSITIVE_ORDER is locale-independent (ASCII), so it avoids the Turkish 'I/ı'
	// pitfall that a manual toLowerCase() on keys would hit.
	public final static ConcurrentMap<String, YAMLProcessor> YAML_STORE = new ConcurrentSkipListMap<String, YAMLProcessor>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * Null-safe lookup into {@link #YAML_STORE}. The backing {@link ConcurrentSkipListMap} is sorted by a
	 * comparator ({@link String#CASE_INSENSITIVE_ORDER}) and therefore throws a {@link NullPointerException}
	 * when queried with a {@code null} key (the comparator can't compare null). Scripts can easily produce a
	 * null id, e.g. {@code yaml value "x" from ("ps-" + name of attacker)} where the right-hand side is
	 * unknown, so all store access funnels through these guards to fail gracefully (treat a null id as
	 * "no such yaml") instead of crashing Skript with a severe error.
	 *
	 * @param id the yaml id/name, may be null
	 * @return the matching processor, or null if id is null or not loaded
	 */
	public static YAMLProcessor getYaml(String id) {
		return id == null ? null : YAML_STORE.get(id);
	}

	/**
	 * Null-safe {@link #YAML_STORE} membership test. See {@link #getYaml(String)} for why this guard exists.
	 *
	 * @param id the yaml id/name, may be null
	 * @return true only if id is non-null and a yaml with that id is loaded
	 */
	public static boolean hasYaml(String id) {
		return id != null && YAML_STORE.containsKey(id);
	}

	/**
	 * Null-safe removal from {@link #YAML_STORE}. See {@link #getYaml(String)} for why this guard exists.
	 *
	 * @param id the yaml id/name, may be null
	 * @return the removed processor, or null if id is null or not loaded
	 */
	public static YAMLProcessor removeYaml(String id) {
		return id == null ? null : YAML_STORE.remove(id);
	}

	private static SkriptYaml instance;
	private int serverVersion;
	private SkriptAdapter adapter;
	private UpdateChecker updateChecker;

	private final static HashMap<String, String> REGISTERED_TAGS = new HashMap<String, String>();
	private static SkriptYamlRepresenter representer;
	private static SkriptYamlConstructor constructor;

	public SkriptYaml() {
		if (instance == null) {
			instance = this;
		} else {
			throw new IllegalStateException();
		}
	}

	public static boolean isTagRegistered(String tag) {
		return REGISTERED_TAGS.containsKey(tag);
	}

	/**
	 * Registers a tag (ie. !location) to a class using a supplied represented and constructed class.
	 * <br><br>
	 * 
	 * <b>Fails to register if:</b><br>
	 * <ol>
	 * <li> the class being registered doesn't match the type used in the constructed and represented classes
	 * <li> the class is already registered
	 * <li> the tag is already registered
	 * <ol>
	 * <br>
	 * @param plugin 
	 * @param tag tag being registered
	 * @param c class being registered
	 * @param rc represented class
	 * @param cc constructed class
	 * <br>
	 * @see RepresentedClass
	 * @see ConstructedClass
	 * 
	 */
	public static void registerTag(JavaPlugin plugin, String tag, Class<?> c, RepresentedClass<?> rc, ConstructedClass<?> cc) {
		String prefix = plugin.getName().toLowerCase() + "-";
		if (!tag.startsWith(prefix))
			tag = prefix + tag;
		if (!REGISTERED_TAGS.containsKey(tag)) {
			if (!representer.contains(c)) {
				if (SkriptYamlUtils.getType(rc.getClass()) == c) {
					if (SkriptYamlUtils.getType(cc.getClass()) == c) {
						REGISTERED_TAGS.put(tag, plugin.getName());
						representer.register(tag, c, rc);
						constructor.register(tag, cc);
					} else {
						warn("The class '" + c.getSimpleName() + "' that the plugin '" + plugin.getName()
								+ "' is trying to register does not match constructed class '"
								+ SkriptYamlUtils.getType(cc.getClass()).getSimpleName() + "' for constructor '"
								+ cc.getClass().getSimpleName() + "' the tag '" + tag + "' was not registered");
					}
				} else {
					warn("The class '" + c.getSimpleName() + "' that the plugin '" + plugin.getName()
							+ "' is trying to register does not match represented class '"
							+ SkriptYamlUtils.getType(rc.getClass()).getSimpleName() + "' for representer '"
							+ rc.getClass().getSimpleName() + "' the tag '" + tag + "' was not registered");
				}
			} else {
				warn("The class '" + c.getSimpleName() + "' that the plugin '" + plugin.getName()
						+ "' is trying to register for the tag '" + tag + "' is already registered");
			}
		} else {
			warn("The plugin '" + plugin.getName() + "' is trying to register the tag '" + tag
					+ "' but it's already registered to '" + REGISTERED_TAGS.get(tag) + "'");
		}
	}

	@Override
	public void onEnable() {
		Plugin skript = Bukkit.getServer().getPluginManager().getPlugin("Skript");
		if (skript != null) {
			serverVersion = Skript.getMinecraftVersion().getMinor();
			if (Skript.isAcceptRegistrations()) {
				try {
					SkriptAddon addonInstance = Skript.registerAddon(this);
					addonInstance.loadClasses("me.sashie.skriptyaml", "skript");
				} catch (SkriptAPIException e) {	//SkriptAPIException("Registering is disabled after initialisation!");
					error("Somehow you loaded skript-yaml after Skript has already finished registering addons, which Skript does not allow! Did you load this using a plugin manager?");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (Skript.getVersion().getMajor() >= 3 || (Skript.getVersion().getMajor() >= 2 && Skript.getVersion().getMinor() >= 10))
				adapter = new V2_10();
			else if (Skript.getVersion().getMajor() == 2 && Skript.getVersion().getMinor() >= 8)
				adapter = new V2_8();
			else if (Skript.getVersion().getMajor() == 2 && Skript.getVersion().getMinor() >= 7)
				adapter = new V2_7();
			else if (Skript.getVersion().getMajor() == 2 && Skript.getVersion().getMinor() >= 6)
				adapter = new V2_6();
			else if (Skript.getVersion().getMajor() == 2 && Skript.getVersion().getMinor() >= 4)
				adapter = new V2_4();
			else
				adapter = new V2_3();

			representer = new SkriptYamlRepresenter();
			constructor = new SkriptYamlConstructor();
			
			// new MetricsLite(this);
			Metrics metrics = new Metrics(this, 1814);
			metrics.addCustomChart(
					new DrilldownPie("plugin_tags", new Callable<Map<String, Map<String, Integer>>>() {
						@Override
						public Map<String, Map<String, Integer>> call() throws Exception {
							return registeredTags();
						}
					}));
			updateChecker = new UpdateChecker(this);
		} else {
			Bukkit.getPluginManager().disablePlugin(this);
			error("Skript not found, plugin disabled.");
		}


	}

	@Override
	public void onDisable() {
		AsyncEffect.shutdownExecutor();
		if (updateChecker != null) {
			updateChecker.cancel();
			updateChecker = null;
		}
		YAML_STORE.clear();
		REGISTERED_TAGS.clear();
		SkriptYamlRepresenter.clearRepresentedClasses();
		representer = null;
		constructor = null;
		adapter = null;
		instance = null;
	}
/*
	public String registeredTagsToString() {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Entry<String, Map<String, Integer>>> pluginName = registeredTags().entrySet()
				.iterator(); pluginName.hasNext();) {
			Entry<String, Map<String, Integer>> entry = pluginName.next();
			sb.append("[ ");
			sb.append(entry.getKey());
			sb.append(" ( ");
			for (Iterator<String> tag = entry.getValue().keySet().iterator(); tag.hasNext();) {
				sb.append(tag.next());
				if (tag.hasNext())
					sb.append(", ");
			}
			sb.append(" ) ]");
			if (pluginName.hasNext())
				sb.append("\\n");
		}

		return sb.toString();
	}
*/
	private Map<String, Map<String, Integer>> registeredTags() {
		Map<String, Map<String, Integer>> map = new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> entry;
		for (Iterator<String> iter = REGISTERED_TAGS.keySet().iterator(); iter.hasNext();) {
			String tag = iter.next();
			String pluginName = REGISTERED_TAGS.get(tag);
			if (!map.containsKey(pluginName)) {
				entry = new HashMap<String, Integer>();
			} else {
				entry = map.get(pluginName);
			}
			entry.put(tag, 1);
			map.put(pluginName, entry);
		}
		return map;
	}

	public static SkriptYaml getInstance() {
		if (instance == null) {
			throw new IllegalStateException();
		}
		return instance;
	}

	public SkriptYamlRepresenter getRepresenter() {
		return representer;
	}

	public SkriptYamlConstructor getConstructor() {
		return constructor;
	}

	public int getServerVersion() {
		return serverVersion;
	}

	public SkriptAdapter getSkriptAdapter() {
		return adapter;
	}

	public static void debug(String error) {
		LOGGER.warning("[skript-yaml DEBUG] " + error);
	}

	public static void warn(String error) {
		LOGGER.warning("[skript-yaml] " + error);
	}

	public static void error(String error) {
		LOGGER.severe("[skript-yaml] " + error);
	}
}

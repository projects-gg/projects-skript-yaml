package me.sashie.skriptyaml.utils.yaml;

import me.sashie.skriptyaml.SkriptYaml;
import me.sashie.skriptyaml.debug.SkriptNode;
import me.sashie.skriptyaml.skript.ExprYaml.YamlState;
import me.sashie.skriptyaml.utils.SkriptYamlUtils;
import me.sashie.skriptyaml.utils.StringUtil;
import org.bukkit.ChatColor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;


public class YAMLAPI {

	public static void loadFromDefault(String id, String path, String defaultYamlFile) {
		try (InputStream stream = YAMLAPI.class.getResourceAsStream(defaultYamlFile + ".yml")) {
			if (stream == null) {
				SkriptYaml.error("[Load Yaml] Default yaml '" + defaultYamlFile + ".yml' was not found");
				return;
			}
			Files.copy(stream, Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}

		load(id, path + File.separator + defaultYamlFile + ".yml", false);
	}

	public static boolean load(String id, String file, boolean isNonRelative) {
		File yamlFile = SkriptYamlUtils.getFile(file, isNonRelative);
		return load(id, yamlFile, null);
	}

	public static boolean load(String id, File file, SkriptNode skriptNode) {
		// Resolve case-insensitively so e.g. 'y.yml' picks up an existing 'Y.yml' on case-sensitive
		// filesystems instead of creating a new empty file next to it.
		file = SkriptYamlUtils.resolveIgnoreCase(file);
		try {
			if (!file.exists()) {
				File folder = file.getParentFile();
				if (folder != null && !folder.exists()) {
					folder.mkdirs();
				}
				file.createNewFile();
			}
		} catch (IOException error) {
			SkriptYaml.error("[Load Yaml] " + error.getMessage() + " (" + file.toString() + ") " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}

		YAMLProcessor yaml = new YAMLProcessor(file, false, YAMLFormat.EXTENDED);

		try {
			yaml.load();
			if (yaml.hasLoadFailed())
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (YAMLException e) {
			SkriptYaml.error("[Load Yaml] Snakeyaml " + e.getMessage() + " in file '" + file.getAbsolutePath() + "' (possible loss of data)");
			return false;
		}

		SkriptYaml.YAML_STORE.put(id, yaml);
		return true;
	}

	public static void loadDir(String directory, boolean useFileAsID, boolean root) {
		String name = StringUtil.checkSeparator(directory);

		File dir = null;

		if (root) {
			dir = new File(StringUtil.checkRoot(name));
		} else {
			Path server = Paths.get("").normalize().toAbsolutePath();
			dir = new File(server + File.separator + name);
		}

		if (!dir.isDirectory()) {
			SkriptYaml.warn("[Load Yaml] " + name + " is not a directory!");
			return;
		}

		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				if (filename.endsWith(".yml") || filename.endsWith(".yaml"))
					return true;
				return false;
			}
		});
		if (files == null)
			return;

		for (File yamlFile : files) {
			YAMLProcessor yaml = new YAMLProcessor(yamlFile, false, YAMLFormat.EXTENDED);

			try {
				yaml.load();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (useFileAsID) {
					SkriptYaml.YAML_STORE.put(StringUtil.stripExtention(yamlFile.getName()), yaml);
				} else {
					SkriptYaml.YAML_STORE.put(StringUtil.checkLastSeparator(name) + yamlFile.getName(), yaml);
				}
			}
		}
	}

	public static void save(String id) {
		YAMLProcessor yaml = get(id);
		if (yaml != null)
			yaml.save(true);
	}

	public static String[] loadedYamlNames() {
		if (SkriptYaml.YAML_STORE.isEmpty())
			return null;
		return SkriptYaml.YAML_STORE.keySet().toArray(new String[0]);
	}



	public static YAMLProcessor get(String id) {
		YAMLProcessor yaml = SkriptYaml.getYaml(id);
		if (yaml == null) {
			SkriptYaml.warn("No yaml by the name '" + id + "' has been loaded");
			return null;
		}

		return yaml;
	}

	public static boolean isLoaded(String id) {
		return SkriptYaml.hasYaml(id);
	}

	public static boolean isEmpty(String id) {
		YAMLProcessor yaml = SkriptYaml.getYaml(id);
		return yaml == null || yaml.getAllKeys().isEmpty();
	}

	public static boolean hasValue(String id, String path) {
		YAMLProcessor yaml = SkriptYaml.getYaml(id);
		return yaml != null && yaml.getProperty(path) != null;
	}

	public static Object getValue(String id, String path) {
		YAMLProcessor config = get(id);

		Object o = config.getProperty(path);
		if (o != null) {
			if (String.class.isAssignableFrom(o.getClass()))
				o = ChatColor.translateAlternateColorCodes('&', (String) o);
			return o;
		}
		return null;
	}

	public static List<String> getNodes(String id, String path) {
		YAMLProcessor config = get(id);

		List<String> keys = new ArrayList<String>();
		if (path.equals("")) {
			Set<String> rootNodes = config.getMap().keySet();
			keys.addAll(rootNodes);
			return keys;
		}
		YAMLNode node = config.getNode(path);
		if (node == null)
			return null;
		Map<String, Object> nodes = node.getMap();
		for (String key : nodes.keySet()) {
			keys.add(path + "." + key);
		}
		return keys;
	}

	public static List<String> getNodeKeys(String id, String path) {
		YAMLProcessor config = get(id);

		List<String> nodesKeys = config.getKeys(path);
		if (nodesKeys == null)
			return null;
		return nodesKeys;
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> getList(String id, String path) {
		YAMLProcessor config = get(id);

		List<T> items = (List<T>) config.getList(path);
		if (items == null)
			return null;
		return items;
	}

	public static Object getWithState(String id, String path, YamlState state) {
		YAMLProcessor config = get(id);

		if (state == YamlState.VALUE) {
			Object o = config.getProperty(path);
			if (o != null) {
				if (String.class.isAssignableFrom(o.getClass()))
					o = ChatColor.translateAlternateColorCodes('&', (String) o);
				return o;
			}
			return null;
		} else if (state == YamlState.NODES) {
			if (path.equals("")) {
				Set<String> rootNodes = config.getMap().keySet();
				return rootNodes;
			}
			YAMLNode node = config.getNode(path);
			if (node == null)
				return null;
			Map<String, Object> nodes = node.getMap();
			List<String> keys = new ArrayList<String>();
			for (String key : nodes.keySet()) {
				keys.add(path + "." + key);
			}
			return keys;
		} else if (state == YamlState.NODE_KEYS) {
			List<String> nodesKeys = config.getKeys(path);
			if (nodesKeys == null)
				return null;
			return nodesKeys;
		} else if (state == YamlState.LIST) {
			List<Object> items = config.getList(path);
			if (items == null)
				return null;
			return items;
		}
		return null;
	}

	public static void removeNode(String id, String path) {
		YAMLProcessor config = get(id);

		config.removeProperty(path);
		return;
	}

	public static void setValue(String id, String path, Object delta) {
		YAMLProcessor config = get(id);

		config.setProperty(path, delta);
	}

	public static void addNodeKeys(String id, String path) {
		YAMLProcessor config = get(id);

		config.addNode(path);
	}

	public static void removeNodeKeys(String id, String path) {
		YAMLProcessor config = get(id);

		config.setProperty(path, null);
	}

	@SuppressWarnings("unchecked")
	public static void addToList(String id, String path, Object delta) {
		YAMLProcessor config = get(id);

		List<Object> objects = config.getList(path);

		if (delta instanceof List) {
			if (objects == null)
				config.setProperty(path, delta);
			else {
//System.out.println("add list to list works"); //TODO
				objects.addAll((List<Object>) delta);
				config.setProperty(path, objects);
			}
		} else {
			if (objects == null)
				objects = new ArrayList<Object>();
			objects.add(delta);
			config.setProperty(path, objects);
		}
	}

	public static void removeFromList(String id, String path, Object delta) {
		YAMLProcessor config = get(id);

		List<Object> objects = config.getList(path);
		if (objects == null)
			return;

		objects.remove(delta);
		config.setProperty(path, objects); // Mark as modified
	}

	public static void removeFromList(String id, String path, Object[] delta) {
		YAMLProcessor config = get(id);

		List<Object> objects = config.getList(path);
		if (objects == null)
			return;

		for (Object o : delta)
			objects.remove(o);
		config.setProperty(path, objects); // Mark as modified
	}

	public static <T> void setList(String id, String path, List<T> delta) {
		YAMLProcessor config = get(id);

		List<Object> objects = config.getList(path);

		if (objects == null) {
			config.setProperty(path, delta);
		} else {
			objects.clear();
			objects.addAll(delta);
			config.setProperty(path, objects);
		}
	}

	public enum ChangeMode {
		DELETE, RESET, SET, ADD, REMOVE
	}

	public static void setWithState(String id, String path, List<Object> delta, YamlState state, ChangeMode mode) {
		YAMLProcessor config = get(id);

		if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET) {
			config.removeProperty(path);
			return;
		}

		if (state == YamlState.VALUE) {
			if (mode == ChangeMode.SET)
				config.setProperty(path, delta);
		} else if (state == YamlState.NODE_KEYS) {
			if (mode == ChangeMode.ADD)
				config.addNode(path);
			else if (mode == ChangeMode.REMOVE)
				config.setProperty(path, null);
		} else if (state == YamlState.LIST) {
			List<Object> objects = config.getList(path);

			if (mode == ChangeMode.ADD) {
				if (objects == null)
					config.setProperty(path, delta);
				else {
					objects.addAll(delta);
					config.setProperty(path, objects);
				}
			} else if (mode == ChangeMode.REMOVE) {
				if (objects == null)
					return;
				for (Object o : delta)
					objects.remove(o);
				config.setProperty(path, objects); // Mark as modified
			} else if (mode == ChangeMode.SET) {
				if (objects == null) {
					config.setProperty(path, delta);
				} else {
					objects.clear();
					objects.addAll(delta);
					config.setProperty(path, objects);
				}
			}
		}
	}

	/**
	 * Changes the ID of a loaded YAML file.
	 * 
	 * @param oldId The current ID of the YAML file
	 * @param newId The new ID for the YAML file
	 * @param skriptNode The SkriptNode for error reporting
	 * @return true if the ID was successfully changed, false otherwise
	 */
	public static boolean changeId(String oldId, String newId, SkriptNode skriptNode) {
		if (!SkriptYaml.hasYaml(oldId)) {
			SkriptYaml.warn("No yaml by the name '" + oldId + "' has been loaded " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}

		if (newId == null) {
			SkriptYaml.warn("Cannot change the yaml id to a null name " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}

		if (SkriptYaml.hasYaml(newId)) {
			SkriptYaml.warn("A yaml with the name '" + newId + "' is already loaded " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}

		YAMLProcessor yaml = SkriptYaml.removeYaml(oldId);
		SkriptYaml.YAML_STORE.put(newId, yaml);
		return true;
	}

	/**
	 * Reassigns the file path of a loaded YAML file. This is useful when a file
	 * has been deleted but kept in memory, and you want to save it to a new location.
	 * 
	 * @param id The ID of the YAML file
	 * @param newFilePath The new file path
	 * @param skriptNode The SkriptNode for error reporting
	 * @return true if the file path was successfully reassigned, false otherwise
	 */
	public static boolean reassignFile(String id, String newFilePath, boolean isNonRelative, SkriptNode skriptNode) {
		YAMLProcessor yaml = SkriptYaml.getYaml(id);
		if (yaml == null) {
			SkriptYaml.warn("No yaml by the name '" + id + "' has been loaded " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}

		File newFile = SkriptYamlUtils.getFile(newFilePath, isNonRelative);

		try {
			// Create parent directories if they don't exist
			File parent = newFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			yaml.reassignFile(newFile);
			return true;
		} catch (Exception e) {
			SkriptYaml.warn("Failed to reassign file path for yaml '" + id + "': " + e.getMessage() + " " + (skriptNode != null ? skriptNode.toString() : ""));
			return false;
		}
	}
}

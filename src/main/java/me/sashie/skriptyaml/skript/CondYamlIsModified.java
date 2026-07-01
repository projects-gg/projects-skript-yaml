package me.sashie.skriptyaml.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.sashie.skriptyaml.SkriptYaml;
import me.sashie.skriptyaml.utils.yaml.YAMLProcessor;

import org.bukkit.event.Event;

import javax.annotation.Nullable;

@Name("Is YAML Modified")
@Description("Checks if a YAML file has been modified since it was last loaded or saved.")
@Examples({
    "# Basic check",
    "yaml \"config\" is modified:",
    "    broadcast \"Config has unsaved changes!\"",
    "    save yaml \"config\"",

    "# Negation",
    "yaml \"config\" is not modified:",
    "    broadcast \"Config is up to date!\"",

    "# Synonyms",
    "yaml \"config\" is unsaved:",
    "    save yaml \"config\"",

    "# Auto-save example",
    "every 5 minutes:",
    "    if yaml \"playerdata\" is modified:",
    "        save yaml \"playerdata\"",
    "        broadcast \"Player data auto-saved!\"",

    "# Prevent data loss on quit",
    "on quit:",
    "    if yaml \"playerdata\" is modified:",
    "        save yaml \"playerdata\"",
    "        broadcast \"Saved unsaved changes before shutdown!\"",

    "# Conditional save function",
    "function saveIfModified(yamlId: text):",
    "    if yaml \"%{_yamlId}%\" is modified:",
    "        save yaml \"%{_yamlId}%\"",
    "        return true",
    "    return false",

    "# Comment change triggers modified state",
    "set yaml comment \"A new comment\" in \"config\"",
    "if yaml \"config\" is modified:",
    "    save yaml \"config\""
})
@Since("1.7.2")
public class CondYamlIsModified extends Condition {

	static {
		Skript.registerCondition(CondYamlIsModified.class,
				"y[a]ml %string% is (modified|unsaved)",
				"y[a]ml %string% is not (modified|unsaved)");
	}

	private Expression<String> file;

	@Override
	public boolean check(final Event event) {
		String file = this.file.getSingle(event);
		YAMLProcessor yaml = SkriptYaml.getYaml(file);
		if (yaml == null)
			return isNegated();
		return yaml.isModified() ^ isNegated();
	}

	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		return "yaml " + file.toString(e, debug) + " is " + (isNegated() ? "not " : "") + "modified";
	}

	@SuppressWarnings({"unchecked"})
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
		file = (Expression<String>) exprs[0];
		setNegated(matchedPattern == 1);
		return true;
	}
} 
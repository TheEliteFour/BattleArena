package mc.alk.arena.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class NotifierUtil {
	public static Map<String,Set<String>> listeners = new ConcurrentHashMap<String,Set<String>>();

	public static void notify(String type, String msg) {
        if (listeners.get(type)== null)
            return;
		for (String name: listeners.get(type)){
			Player p = ServerUtil.findPlayerExact(name);
			if (p== null || !p.isOnline())
				continue;
			MessageUtil.sendMessage(p, msg);
		}
	}

	public static void notify(String type, Throwable exception) {
        if (listeners.get(type)== null)
			return;
		StringBuilder sb = new StringBuilder();
		for (StackTraceElement e: exception.getStackTrace()){
			sb.append(e.toString());
		}
		String msg = sb.toString();
        for (String name: listeners.get(type)){
			Player p = ServerUtil.findPlayerExact(name);
			if (p== null || !p.isOnline())
				continue;
			MessageUtil.sendMessage(p, msg);
		}
	}

	public static void addListener(Player player, String type) {
		Set<String> players = listeners.get(type);
		if (players == null){
			players = new CopyOnWriteArraySet<String>();
			listeners.put(type, players);
		}
		players.add(player.getName());
	}

	public static void removeListener(Player player, String type) {
		Set<String> players = listeners.get(type);
		if (players != null){
			players.remove(player.getName());
			if (players.isEmpty())
				listeners.remove(type);
		}
	}

	public static boolean hasListener(String type) {
		return listeners.containsKey(type) && !listeners.get(type).isEmpty();
	}


}

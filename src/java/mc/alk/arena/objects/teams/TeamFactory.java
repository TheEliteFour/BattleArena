package mc.alk.arena.objects.teams;

import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.util.Log;
import mc.alk.arena.util.TeamUtil;

import java.lang.reflect.Constructor;
import java.util.Set;

public class TeamFactory {

	public static ArenaTeam createCompositeTeam(int index, MatchParams params, ArenaPlayer p) {
        CompositeTeam ct = (CompositeTeam) createTeam(index, params, CompositeTeam.class);
        ct.addPlayer(p);
        return ct;
    }

    public static CompositeTeam createCompositeTeam(int index, MatchParams params, Set<ArenaPlayer> players) {
        CompositeTeam ct = (CompositeTeam) createTeam(index, params, CompositeTeam.class);
        ct.addPlayers(players);
        return ct;
    }

    public static CompositeTeam createCompositeTeam(MatchParams params, Set<ArenaPlayer> players) {
        CompositeTeam ct = (CompositeTeam) createTeam(-1, params, CompositeTeam.class);
        ct.addPlayers(players);
		return ct;
	}

	public static CompositeTeam createCompositeTeam() {
		return new CompositeTeam();
	}

	public static ArenaTeam createTeam(Class<? extends ArenaTeam> clazz) {
		Class<?>[] args = {};
		try {
			Constructor<?> constructor = clazz.getConstructor(args);
			return (ArenaTeam) constructor.newInstance((Object[])args);
		} catch (NoSuchMethodException e){
			System.err.println("If you have custom constructors for your Team you must also have a public default constructor");
			System.err.println("Add the following line to your Team Class '" + clazz.getSimpleName()+".java'");
			System.err.println("public " + clazz.getSimpleName()+"(){}");
		} catch (Exception e) {
			Log.printStackTrace(e);
		}
		return null;
	}

    public static ArenaTeam createTeam(Integer index, MatchParams params, Class<? extends ArenaTeam> clazz) {
        ArenaTeam at = createTeam(clazz);
        if (at == null)
            return null;
        if (index != null && (index == -1 || params.getTeamParams() == null || !params.getTeamParams().containsKey(index))){
            at.setMinPlayers(params.getMinTeamSize());
            at.setMaxPlayers(params.getMaxTeamSize());
        } else {
            MatchParams tp = params.getTeamParams().get(index);
            at.setMinPlayers(tp.getMinTeamSize());
            at.setMaxPlayers(tp.getMaxTeamSize());
        }
        at.setCurrentParams(params);
        if (index != null && index != -1){
            at.setIndex(index);
        }
        TeamUtil.initTeam(at, params);
        return at;
    }


    public static void setStringID(AbstractTeam team, String newID) {
        team.strID = newID;
    }
}

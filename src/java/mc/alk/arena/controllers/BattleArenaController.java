package mc.alk.arena.controllers;

import mc.alk.arena.BattleArena;
import mc.alk.arena.Defaults;
import mc.alk.arena.competition.match.ArenaMatch;
import mc.alk.arena.competition.match.Match;
import mc.alk.arena.controllers.joining.AbstractJoinHandler;
import mc.alk.arena.controllers.joining.TeamJoinFactory;
import mc.alk.arena.controllers.containers.GameManager;
import mc.alk.arena.controllers.containers.RoomContainer;
import mc.alk.arena.events.matches.MatchFinishedEvent;
import mc.alk.arena.listeners.SignUpdateListener;
import mc.alk.arena.objects.ArenaParams;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.ContainerState;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.arenas.ArenaControllerInterface;
import mc.alk.arena.objects.arenas.ArenaListener;
import mc.alk.arena.objects.arenas.ArenaType;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.exceptions.NeverWouldJoinException;
import mc.alk.arena.objects.options.EventOpenOptions;
import mc.alk.arena.objects.options.EventOpenOptions.EventOpenOption;
import mc.alk.arena.objects.options.JoinOptions;
import mc.alk.arena.objects.options.TransitionOption;
import mc.alk.arena.objects.pairs.JoinResult;
import mc.alk.arena.objects.joining.ArenaMatchQueue;
import mc.alk.arena.objects.joining.MatchTeamQObject;
import mc.alk.arena.objects.joining.TeamJoinObject;
import mc.alk.arena.objects.joining.WaitingObject;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.util.Log;
import mc.alk.arena.util.PlayerUtil;
import mc.alk.arena.util.ServerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class BattleArenaController implements Runnable, ArenaListener, Listener{

    private boolean stop = false;
    private boolean running = false;

    final private Set<Match> running_matches = Collections.synchronizedSet(new CopyOnWriteArraySet<Match>());
    final private Map<ArenaType,LinkedList<Match>> unfilled_matches = Collections.synchronizedMap(new ConcurrentHashMap<ArenaType,LinkedList<Match>>());
    private Map<String, Arena> allarenas = new ConcurrentHashMap<String, Arena>();
    final private Map<ArenaType,OldLobbyState> oldLobbyState = new HashMap<ArenaType,OldLobbyState>();
    private final ArenaMatchQueue amq = new ArenaMatchQueue();
    final SignUpdateListener signUpdateListener;

    final private Map<ArenaType, Arena> fixedArenas = new HashMap<ArenaType, Arena>();


    public class OldLobbyState{
        ContainerState pcs;
        Set<Match> running = new HashSet<Match>();
        public boolean isEmpty() {return running.isEmpty();}
        public void add(Match am){running.add(am);}
        public boolean remove(Match am) {return running.remove(am);}
    }


    public BattleArenaController(SignUpdateListener signUpdateListener){
        MethodController methodController = new MethodController("BAC");
        methodController.addAllEvents(this);
        try{Bukkit.getPluginManager().registerEvents(this, BattleArena.getSelf());}catch(Exception e){/* keep on truckin'*/}
        this.signUpdateListener = signUpdateListener;
    }

    /// Run is Thread Safe
    public void run() {
        running = true;
        Match match;
        while (!stop){
            match = amq.getArenaMatch();
            if (match != null){
                Bukkit.getScheduler().scheduleSyncDelayedTask(BattleArena.getSelf(), new OpenAndStartMatch(match));
            }
        }
        running = false;
    }

    /**
     * opens and starts the match
     */
    private class OpenAndStartMatch implements Runnable{
        Match match;
        public OpenAndStartMatch(Match match){
            this.match = match;
        }
        @Override
        public void run() {
            openMatch(match);
            match.run();
        }
    }

    public Match createMatch(Arena arena, EventOpenOptions eoo) throws NeverWouldJoinException {
        final ArenaMatch arenaMatch = new ArenaMatch(arena, eoo.getParams(),null);
        AbstractJoinHandler jh = TeamJoinFactory.createTeamJoinHandler(eoo.getParams(), arenaMatch);
        arenaMatch.hookTeamJoinHandler(jh);
        Bukkit.getScheduler().scheduleSyncDelayedTask(BattleArena.getSelf(), new Runnable(){
            @Override
            public void run() {
                openMatch(arenaMatch);
//                amq.fillMatch(arenaMatch);
            }
        });
        return arenaMatch;
    }

    private void setFixedReservedArena(Arena arena) {
        fixedArenas.put(arena.getArenaType(), arena);
    }

    private void removeFixedReservedArena(Arena arena) {
        Arena a = fixedArenas.get(arena.getArenaType());
        if (a != null && a.matches(arena)) {
            fixedArenas.remove(arena.getArenaType());
        }
    }

    public Match createAndAutoMatch(Arena arena, EventOpenOptions eoo)
            throws NeverWouldJoinException, IllegalStateException {
        MatchParams mp = eoo.getParams();
        MatchParams oldArenaParams = new MatchParams(arena.getParams());

        mp.setForceStartTime((long) eoo.getSecTillStart());
        ArenaParams parent = mp.getParent();
        mp.setParent(null);
        mp.setParent(parent);
        /// Since we want people to add this event, add this arena as the next
        setFixedReservedArena(arena);

        arena.setParams(mp);
        Match m = createMatch(arena,eoo);
        m.setOldArenaParams(oldArenaParams);
        saveStates(m,arena);
        arena.setAllContainerState(ContainerState.OPEN);
        m.setTimedStart(eoo.getSecTillStart(), eoo.getInterval());
        amq.incNumberOpenMatches(mp.getType());


        if (eoo.hasOption(EventOpenOption.FORCEJOIN)){
            addAllOnline(m.getParams(), arena);}

        return m;
    }

    private void addAllOnline(MatchParams mp, Arena arena) {
        Player[] online = ServerUtil.getOnlinePlayers();
        String cmd = mp.getCommand() +" add "+arena.getName();
        for (Player p: online){
            PlayerUtil.doCommand(p, cmd);
        }
    }

    private void saveStates(Match m, Arena arena) {
        /// save the old states to put back after the match
        if (RoomController.hasLobby(arena.getArenaType())){
            RoomContainer pc = RoomController.getLobby(arena.getArenaType());
            OldLobbyState ols = oldLobbyState.get(arena.getArenaType());
            if (ols == null){
                ols = new OldLobbyState();
                ols.pcs = pc.getContainerState();
                oldLobbyState.put(arena.getArenaType(), ols);
            }
            ols.add(m);
        }
    }

    private void openMatch(Match match){
        match.addArenaListener(this);
        synchronized(running_matches){
            running_matches.add(match);
        }

        match.open();
        if (match.isJoinablePostCreate()){
            LinkedList<Match> matches = unfilled_matches.get(match.getParams().getType());
            if (matches == null){
                matches = new LinkedList<Match>();
                unfilled_matches.put(match.getParams().getType(), matches);
            }
            matches.addFirst(match);
        } else {
            removeFixedReservedArena(match.getArena());
        }
    }

    private void restoreStates(Match am, Arena arena){
        if (arena == null)
            arena = am.getArena();
        OldLobbyState ols = oldLobbyState.get(arena.getArenaType());
        if (ols != null){ /// we only put back the lobby state if its the last autoed match finishing
            if (ols.remove(am) && ols.isEmpty()){
                RoomController.getLobby(am.getArena().getArenaType()).setContainerState(ols.pcs);
            }
        }
    }

    public void startMatch(Match arenaMatch) {
        /// arenaMatch run calls.... broadcastMessage ( which unfortunately is not thread safe)
        /// So we have to schedule a sync task... again
        Bukkit.getScheduler().scheduleSyncDelayedTask(BattleArena.getSelf(), arenaMatch);
    }


    @ArenaEventHandler
    public void matchFinished(MatchFinishedEvent event){
        if (Defaults.DEBUG ) Log.info("BattleArenaController::matchFinished=" + this + ":" );
        Match am = event.getMatch();
        removeMatch(am); /// handles removing running match from the BArenaController

        final Arena arena = allarenas.get(am.getArena().getName().toUpperCase());
        if (arena == null) { /// we have deleted this arena while a match was going on
            return;}

        /// put back old states if it was autoed
        restoreStates(am, arena);
        removeFixedReservedArena(arena);
        if (am.getParams().hasOptionAt(MatchState.ONCOMPLETE, TransitionOption.REJOIN)){
            MatchParams mp = am.getParams();
            List<ArenaPlayer> players = am.getNonLeftPlayers();
            String[] args = {};
            for (ArenaPlayer ap: players){
                BattleArena.getBAExecutor().join(ap, mp, args);
            }
        }
        /// isEnabled to check to see if we are shutting down
        if (BattleArena.getSelf().isEnabled()){
            Bukkit.getScheduler().scheduleSyncDelayedTask(BattleArena.getSelf(), new Runnable(){
                @Override
                public void run() {
                    amq.add(arena); /// add it back into the queue
                }
            }, am.getParams().getArenaCooldown()*20L);
        }
    }

    public void updateArena(Arena arena) {
        allarenas.put(arena.getName().toUpperCase(), arena);
        if (amq.removeArena(arena) != null){ /// if its not being used
            amq.add(arena);}
    }

    public void addArena(Arena arena) {
        allarenas.put(arena.getName().toUpperCase(), arena);
        amq.add(arena);
    }


    public Map<String, Arena> getArenas(){return allarenas;}

    /**
     * Add the TeamQueueing object to the queue
     * @param tqo the TeamQueueing object to the queue
     * @return JoinResult
     */
    public JoinResult wantsToJoin(TeamJoinObject tqo) throws IllegalStateException {
        /// Can they add an existing Game
        JoinResult jr = joinExistingMatch(tqo);
        if (jr.status == JoinResult.JoinStatus.ADDED_TO_EXISTING_MATCH) {
            return jr;
        }

        /// Add a default arena if they havent specified
        if (!tqo.getJoinOptions().hasArena()) {
            tqo.getJoinOptions().setArena(getNextArena(tqo.getMatchParams(), tqo.getJoinOptions()));
        }

        /// We don't want them to add a queue if they can't fit
        if (tqo.getJoinOptions().getArena() != null &&
                tqo.getJoinOptions().getArena().getParams().hasOptionAt(MatchState.DEFAULTS,TransitionOption.ALWAYSOPEN)){
            if (this.getArenas(tqo.getMatchParams()).size()==1 &&
                    amq.getNumberOpenMatches(tqo.getMatchParams().getType()) >= 1)
                throw new IllegalStateException("&cThe arena " +
                        tqo.getJoinOptions().getArena().getDisplayName() + "&c is currently in use");
        }
        jr = amq.join(tqo);

        MatchParams mp = tqo.getMatchParams();
        if (jr.params == null)
            jr.params = mp;

        /// who is responsible for doing what
        if (Defaults.DEBUG)
            Log.info(" Join status = " + jr.status + "    " + tqo.getTeam() + "   " + tqo.getTeam().getId() + " --"
                    + ", haslobby=" + mp.needsLobby() + "  ,wr=" + (mp.hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTWAITROOM)) + "  " +
                    "   --- hasArena=" + tqo.getJoinOptions().hasArena());
        if (tqo.getJoinOptions().hasArena()) {
            Arena a = tqo.getJoinOptions().getArena();
            if (!(a.getParams().hasOptionAt(MatchState.DEFAULTS, TransitionOption.ALWAYSOPEN) ||
                    a.getParams().hasOptionAt(MatchState.ONJOIN, TransitionOption.ALWAYSJOIN)) &&
                    mp.hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTIN) && BattleArena.getBAController().getMatch(a) != null) {
                throw new IllegalStateException("&cThe arena " + a.getDisplayName() + "&c is currently in use");
            }
        }
        switch (jr.status) {
            case ADDED_TO_ARENA_QUEUE:
            case ADDED_TO_QUEUE:
                break;
            case NONE:
                break;
            case ERROR:
            case ADDED_TO_EXISTING_MATCH:
            case STARTED_NEW_GAME:
                return jr;
            default:
                break;
        }
        if (mp.needsLobby()) {
            if (!RoomController.hasLobby(mp.getType())) {
                throw new IllegalStateException("&cLobby is not set for the " + mp.getName());
            }
            RoomController.getLobby(mp.getType()).teamJoining(tqo.getTeam());
        }
        if (tqo.getJoinOptions().hasArena()) {
            Arena a = tqo.getJoinOptions().getArena();
            if (a.getParams().hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTWAITROOM) ||
                    mp.hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTMAINWAITROOM)) {
                if (a.getWaitroom() == null) {
                    throw new IllegalStateException("&cWaitroom is not set for this arena");
                }
                a.getWaitroom().teamJoining(tqo.getTeam());
            } else if (a.getParams().hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTSPECTATE)) {
                if (a.getSpectatorRoom() == null) {
                    throw new IllegalStateException("&cSpectate is not set for this arena");
                }
                a.getSpectatorRoom().teamJoining(tqo.getTeam());
            } else if (a.getParams().hasOptionAt(MatchState.ONJOIN, TransitionOption.TELEPORTIN)) {
                tqo.getJoinOptions().getArena().teamJoining(tqo.getTeam());
            }
        }
        for (ArenaTeam at : tqo.getTeams()) {
            for (ArenaPlayer ap : at.getPlayers()) {
                ap.getMetaData().setJoinOptions(tqo.getJoinOptions());
            }
        }
        return jr;
    }

    private JoinResult joinExistingMatch(TeamJoinObject tqo) {
        JoinResult jr = new JoinResult();
        jr.params = tqo.getMatchParams();
        if (unfilled_matches.isEmpty()) {
            return jr;
        }
        MatchParams params = tqo.getMatchParams();
        synchronized (unfilled_matches) {
            List<Match> matches = unfilled_matches.get(params.getType());
            if (matches == null)
                return jr;
            for (Match match : matches) {
                /// We dont want people joining in a non waitroom state
                if (!match.canStillJoin()) {
                    continue;
                }
                if (match.getParams().matches(params)) {
                    AbstractJoinHandler tjh = match.getTeamJoinHandler();
                    if (tjh == null)
                        continue;
                    if (!JoinOptions.matches(tqo.getJoinOptions(), match))
                        continue;
                    AbstractJoinHandler.TeamJoinResult tjr = tjh.joiningTeam(tqo);
                    switch (tjr.status) {
                        case ADDED:
                        case ADDED_TO_EXISTING:
                        case ADDED_STILL_NEEDS_PLAYERS:
                            jr.status = JoinResult.JoinStatus.ADDED_TO_EXISTING_MATCH;
                            break;
                        case CANT_FIT:
                            break;
                    }
                    return jr;
                }
            }
        }
        return jr;
    }

    public boolean isInQue(ArenaPlayer p) {return amq.isInQue(p);}

    public void addMatchup(MatchTeamQObject m) {
        amq.join(m);
    }

    public Arena reserveArena(Arena arena) {return amq.reserveArena(arena);}
    public Arena getArena(String arenaName) {return allarenas.get(arenaName.toUpperCase());}

    public Arena removeArena(Arena arena) {
        amq.removeArena(arena);
        allarenas.remove(arena.getName().toUpperCase());
        return arena;
    }

    public void deleteArena(Arena arena) {
        removeArena(arena);
        ArenaControllerInterface ai = new ArenaControllerInterface(arena);
        ai.delete();
    }

    public void arenaChanged(Arena arena){
        try{
            if (removeArena(arena) != null){
                addArena(arena);}
        } catch (Exception e){
            Log.printStackTrace(e);
        }
    }

    public Arena getNextArena(MatchParams mp, JoinOptions jo) {
        if (fixedArenas.containsKey(mp.getType()))
            return fixedArenas.get(mp.getType());
        return amq.getNextArena(mp, jo);
    }

    public Arena nextArenaByMatchParams(MatchParams mp){
        return amq.getNextArena(mp,null);
    }

    public Arena getArenaByMatchParams(MatchParams mp, JoinOptions jp) {
        for (Arena a : allarenas.values()){
            if (a.valid() && a.matches(mp,jp)){
                return a;}
        }
        return null;
    }

    public List<Arena> getArenas(MatchParams mp) {
        List<Arena> arenas = new ArrayList<Arena>();
        for (Arena a : allarenas.values()){
            if (a.valid() && a.matches(mp,null)){
                arenas.add(a);}
        }
        return arenas;
    }

    public Arena getArenaByNearbyMatchParams(MatchParams mp, JoinOptions jp) {
        Arena possible = null;
        int sizeDif = Integer.MAX_VALUE;
        int m1 = mp.getMinTeamSize();
        for (Arena a : allarenas.values()){
            if (!a.valid() || a.getArenaType() != mp.getType())
                continue;
            if (a.matchesIgnoreSize(mp,jp)){
                return a;}
            int m2 = a.getParams().getMinTeamSize();
            if (m2 > m1 && m2 -m1 < sizeDif){
                sizeDif = m2 - m1;
                possible = a;
            }
        }
        return possible;
    }


    public Map<Arena,List<String>> getNotMachingArenaReasons(MatchParams mp, JoinOptions jp) {
        Map<Arena,List<String>> reasons = new HashMap<Arena, List<String>>();
        for (Arena a : allarenas.values()){
            if (a.getArenaType() != mp.getType()){
                continue;
            }
            if (jp != null && !jp.matches(a))
                continue;
            if (!a.valid()){
                reasons.put(a, a.getInvalidReasons());}
            if (!a.matches(mp,jp)){
                List<String> rs = reasons.get(a);
                if (rs == null){
                    reasons.put(a, a.getInvalidMatchReasons(mp, jp));
                } else {
                    rs.addAll(a.getInvalidMatchReasons(mp, jp));
                }
            }
        }
        return reasons;
    }

    //	/**
    //	 * We dont care if they leave queues
    //	 */
    //	@Override
    //	public boolean canLeave(ArenaPlayer p) {
    //		return true;
    //	}

    public boolean hasArenaSize(int i) {return getArenaBySize(i) != null;}
    public Arena getArenaBySize(int i) {
        for (Arena a : allarenas.values()){
            if (a.getParams().matchesTeamSize(i)){
                return a;}
        }
        return null;
    }

    private void removeMatch(Match am){
        synchronized(running_matches){
            running_matches.remove(am);
        }
        synchronized(unfilled_matches){
            List<Match> matches = unfilled_matches.get(am.getParams().getType());
            if (matches != null){
                matches.remove(am);
            }
        }
    }

    public synchronized void stop() {
        if (stop)
            return;
        stop = true;
        amq.stop();
        amq.purgeQueue();
        synchronized(running_matches){
            for (Match am: running_matches){
                cancelMatch(am);
                Arena a = am.getArena();
                if (a != null){
                    Arena arena = allarenas.get(a.getName().toUpperCase());
                    if (arena != null)
                        amq.add(arena);
                }
            }
            running_matches.clear();
        }
    }

    public void resume() {
        stop = false;
        amq.resume();
        if (!running){
            new Thread(this).start();
        }
    }

    public boolean cancelMatch(Arena arena) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.getArena().getName().equals(arena.getName())){
                    cancelMatch(am);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean cancelMatch(ArenaPlayer p) {
        Match am = getMatch(p);
        if (am==null)
            return false;
        cancelMatch(am);
        return true;
    }

    public boolean cancelMatch(ArenaTeam team) {
        Set<ArenaPlayer> ps = team.getPlayers();
        return !ps.isEmpty() && cancelMatch(ps.iterator().next());
    }

    public void cancelMatch(Match am){
        am.cancelMatch();
        for (ArenaTeam t: am.getTeams()){
            t.sendMessage("&4!!!&e This arena match has been cancelled");
        }
    }

    public Match getArenaMatch(Arena a) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.getArena().getName().equals(a.getName())){
                    return am;
                }
            }
        }
        return null;
    }

    public boolean insideArena(ArenaPlayer p) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.isHandled(p)){
                    return true;
                }
            }
        }
        return false;
    }

    public Match getMatch(ArenaPlayer p) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.hasPlayer(p)){
                    return am;
                }
            }
        }
        return null;
    }

    public Match getMatch(Arena arena) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.getArena().equals(arena)){
                    return am;
                }
            }
        }
        return null;
    }

    @Override
    public String toString(){
        return "[BAC]";
    }

    public String toStringQueuesAndMatches(){
        StringBuilder sb = new StringBuilder();
        sb.append(amq.toStringReadyMatches());
        sb.append("------ running  matches -------\n");
        synchronized(running_matches){
            for (Match am : running_matches)
                sb.append(am).append("\n");
        }
        return sb.toString();
    }

    public String toStringArenas(){
        StringBuilder sb = new StringBuilder();
        sb.append(amq.toStringArenas());
        sb.append("------ arenas -------\n");
        for (Arena a : allarenas.values()){
            sb.append(a).append("\n");
        }
        return sb.toString();
    }


    public void removeAllArenas() {
        synchronized(running_matches){
            for (Match am: running_matches){
                am.cancelMatch();
            }
        }

        amq.stop();
        amq.removeAllArenas();
        allarenas.clear();
        amq.resume();
    }

    public void removeAllArenas(ArenaType arenaType) {
        synchronized(running_matches){
            for (Match am: running_matches){
                if (am.getArena().getArenaType() == arenaType)
                    am.cancelMatch();
            }
        }

        amq.stop();
        amq.removeAllArenas(arenaType);
        Iterator<Arena> iter = allarenas.values().iterator();
        while (iter.hasNext()){
            Arena a = iter.next();
            if (a != null && a.getArenaType() == arenaType){
                iter.remove();}
        }
        amq.resume();
    }

    public void cancelAllArenas() {
        amq.stop();
        amq.clearTeamQueues();
        synchronized(running_matches){
            for (Match am: running_matches){
                am.cancelMatch();
            }
        }
        GameManager.cancelAll();
        amq.resume();
    }


    public Collection<ArenaTeam> purgeQueue() {
        return amq.purgeQueue();
    }

    public boolean hasRunningMatches() {
        return !running_matches.isEmpty();
    }

    public WaitingObject getQueueObject(ArenaPlayer player) {
        return amq.getQueueObject(player);
    }

    public List<String> getInvalidQueueReasons(WaitingObject qo) {
        return amq.invalidReason(qo);
    }

    public boolean forceStart(MatchParams mp, boolean respectMinimumPlayers) {
        boolean started = false;
        if (!unfilled_matches.isEmpty()) {
            synchronized (unfilled_matches) {
                List<Match> matches = unfilled_matches.get(mp.getType());
                if (matches != null){
                    for (Match match : matches) {
                        if (match.isTimedStart()){
                            match.setTimedStart(0,0); /// start now! :)
                            started = true;
                        }
                    }
                }
            }
        }
        return amq.forceStart(mp, respectMinimumPlayers) || started;
    }

    public Collection<ArenaPlayer> getPlayersInAllQueues(){
        return amq.getPlayersInAllQueues();
    }
    public Collection<ArenaPlayer> getPlayersInQueue(MatchParams params){
        return amq.getPlayersInQueue(params);
    }

    public String queuesToString() {
        return amq.queuesToString();
    }

    public boolean isQueueEmpty() {
        Collection<ArenaPlayer> col = getPlayersInAllQueues();
        return col == null || col.isEmpty();
    }

    public ArenaMatchQueue getArenaMatchQueue() {
        return amq;
    }


    public List<Match> getRunningMatches(MatchParams params){
        List<Match> list = new ArrayList<Match>();
        synchronized(running_matches){
            for (Match m: running_matches){
                if (m.getParams().getType() == params.getType()){
                    list.add(m);
                }
            }
            return list;
        }
    }

    public Match getSingleMatch(MatchParams params) {
        Match match = null;
        synchronized(running_matches){
            for (Match m: running_matches){
                if (m.getParams().getType() == params.getType()){
                    if (match != null)
                        return null;
                    match = m;
                }
            }
        }
        return match;
    }

    public void openAll(MatchParams mp) {
        for (Arena arena : getArenas(mp)) {
            arena.setAllContainerState(ContainerState.OPEN);
        }
    }

}

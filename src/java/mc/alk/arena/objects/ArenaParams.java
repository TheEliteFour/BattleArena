package mc.alk.arena.objects;

import mc.alk.arena.BattleArena;
import mc.alk.arena.controllers.ParamController;
import mc.alk.arena.objects.arenas.ArenaType;
import mc.alk.arena.objects.options.TransitionOption;
import mc.alk.arena.util.MinMax;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.Entry;


public class ArenaParams {
    ArenaType arenaType;
    Boolean rated;

    String name;
    String cmd;

    Integer timeBetweenRounds;
    Integer secondsTillMatch;
    Integer secondsToLoot;

    MatchTransitions allTops;
    String dbName;

    ArenaParams parent;
    MinMax nTeams;
    MinMax teamSize;
    Boolean closeWaitroomWhileRunning;
    Boolean cancelIfNotEnoughPlayers;
    Integer arenaCooldown;
    Integer allowedTeamSizeDifference;

    private Map<Integer, MatchParams> teamParams;

    public ArenaParams() {}

    public ArenaParams(ArenaType at) {
        this.arenaType = at;
    }

    public ArenaParams(ArenaParams ap) {
        this(ap.getType());
        this.copy(ap);
    }

    public void copy(ArenaParams ap){
        if (this == ap)
            return;
        this.arenaType = ap.arenaType;
        this.rated = ap.rated;
        this.cmd = ap.cmd;
        this.name = ap.name;
        this.timeBetweenRounds = ap.timeBetweenRounds;
        this.secondsTillMatch = ap.secondsTillMatch;
        this.secondsToLoot = ap.secondsToLoot;
        this.dbName = ap.dbName;
        this.closeWaitroomWhileRunning = ap.closeWaitroomWhileRunning;
        this.cancelIfNotEnoughPlayers= ap.cancelIfNotEnoughPlayers;
        this.arenaCooldown = ap.arenaCooldown;
        this.allowedTeamSizeDifference = ap.allowedTeamSizeDifference;
        if (ap.allTops != null)
            this.allTops = new MatchTransitions(ap.allTops);
        if (ap.nTeams != null)
            this.nTeams = new MinMax(ap.nTeams);
        if (ap.teamSize != null)
            this.teamSize = new MinMax(ap.teamSize);
        this.teamParams = ap.teamParams;
        this.parent = ap.parent;
    }

    public void flatten() {
        if (parent == null){
            return;}
        if (this.arenaType == null) this.arenaType = parent.getType();
        if (this.rated == null) this.rated = parent.isRated();
        if (this.cmd == null) this.cmd = parent.getCommand();
        if (this.name == null) this.name = parent.getName();
        if (this.timeBetweenRounds == null) this.timeBetweenRounds = parent.getTimeBetweenRounds();
        if (this.secondsTillMatch == null) this.secondsTillMatch = parent.getSecondsTillMatch();
        if (this.secondsToLoot == null) this.secondsToLoot = parent.getSecondsToLoot();
        if (this.dbName == null) this.dbName = parent.getDBName();
        if (this.closeWaitroomWhileRunning == null) this.closeWaitroomWhileRunning = parent.isWaitroomClosedWhenRunning();
        if (this.cancelIfNotEnoughPlayers == null) this.cancelIfNotEnoughPlayers = parent.isCancelIfNotEnoughPlayers();
        if (this.arenaCooldown== null) this.arenaCooldown = parent.getArenaCooldown();
        if (this.allowedTeamSizeDifference== null) this.allowedTeamSizeDifference= parent.getAllowedTeamSizeDifference();
        this.allTops = mergeChildWithParent(this, parent);
        if (this.nTeams == null && parent.getNTeams()!=null) this.nTeams = new MinMax(parent.getNTeams());
        if (this.teamSize == null && parent.getTeamSizes() !=null) this.teamSize = new MinMax(parent.getTeamSizes());

        if (this.teamParams != null && parent.teamParams != null) {
            HashMap<Integer,MatchParams> tp = new HashMap<Integer,MatchParams>(this.teamParams);
            tp.putAll(parent.getTeamParams());
            this.parent = null;
            this.teamParams = null;
            for (Entry<Integer, MatchParams> e : tp.entrySet()) {
                MatchParams ap = ParamController.copyParams(e.getValue());
                ap.setParent(this);
                ap.flatten();
                tp.put(e.getKey(), ap);
            }
            this.teamParams = tp;
        } else if (parent.teamParams != null){
            HashMap<Integer,MatchParams> tp =  new HashMap<Integer,MatchParams>(parent.teamParams);
            this.parent = null;
            this.teamParams = null;
            for (Entry<Integer, MatchParams> e : tp.entrySet()) {
                MatchParams ap = ParamController.copyParams(e.getValue());
                ap.setParent(this);
                ap.flatten();
                tp.put(e.getKey(), ap);
            }
            this.teamParams = tp;
        } else if (this.teamParams != null){
            HashMap<Integer,MatchParams> tp =  new HashMap<Integer,MatchParams>(this.teamParams);
            this.parent = null;
            this.teamParams = null;
            for (Entry<Integer, MatchParams> e : tp.entrySet()) {
                MatchParams ap = ParamController.copyParams(e.getValue());
                ap.setParent(this);
                ap.flatten();
                tp.put(e.getKey(), ap);
            }
            this.teamParams = tp;
        }
        this.parent = null;
    }

    private MatchTransitions mergeChildWithParent(ArenaParams cap, ArenaParams pap) {
        MatchTransitions mt = cap.allTops == null ? new MatchTransitions() : new MatchTransitions(cap.allTops);
        if (pap != null) {
            MatchTransitions.mergeChildWithParent(mt, mergeChildWithParent(pap, pap.parent));
        }
        return mt;
    }

    public MatchTransitions getTransitionOptions(){
        return allTops != null ? allTops : (parent != null ? parent.getTransitionOptions() : null);
    }

    public void setTransitionOptions(MatchTransitions transitionOptions) {
        this.allTops = transitionOptions;
    }

    public String getTeamSizeRange() {
        return teamSize != null ? teamSize.toString() : (parent != null ? parent.getTeamSizeRange() : "");
    }

    public String getNTeamRange() {
        return nTeams != null ? nTeams.toString() : (parent != null ? parent.getNTeamRange() : "");
    }

    public String getPlayerRange() {
        return ArenaSize.rangeString(getMinPlayers(),getMaxPlayers());
    }

    public ArenaType getType() {return arenaType;}

    public void setType(ArenaType type) {this.arenaType = type;}

    @SuppressWarnings("SimplifiableIfStatement")
    public boolean intersect(ArenaParams params) {
        if (!getType().matches(params.getType()))
            return false;
        if (getNTeams() != null && params.getNTeams() != null && !getNTeams().intersect(params.getNTeams())) {
            return false;}
        return (this.getTeamSizes() != null && params.getTeamSizes() != null &&
                !getTeamSizes().intersect(params.getTeamSizes()));
    }

    public boolean matches(final ArenaParams ap) {
        if (arenaType != null && ap.arenaType != null &&
                arenaType.matches(ap.arenaType)){
            MinMax nt = getNTeams();
            MinMax ts = getTeamSizes();
            if (nt == null || ts == null)
                return true;
            MinMax nt2 = ap.getNTeams();
            MinMax ts2 = ap.getTeamSizes();
            return nt2 == null || ts2 == null || nt.intersect(nt2) && ts.intersect(ts2);
        }
        return false;
    }

    public boolean matchesIgnoreNTeams(final ArenaParams ap) {
        if (arenaType != null && ap.arenaType != null &&
                arenaType.matches(ap.arenaType)){
            MinMax ts = getTeamSizes();
            if (ts == null)
                return true;
            MinMax ts2 = ap.getTeamSizes();
            return ts2 == null ||  ts.intersect(ts2);
        }
        return false;
    }

    public boolean matchesIgnoreSizes(final ArenaParams ap) {
        return arenaType != null && ap.arenaType != null &&
                arenaType.matches(ap.arenaType);
    }


    public Collection<String> getInvalidMatchReasons(ArenaParams ap) {
        List<String> reasons = new ArrayList<String>();
        if (arenaType == null) reasons.add("ArenaType is null");
        if (ap.arenaType == null) reasons.add("Passed params have an arenaType of null");
        else reasons.addAll(arenaType.getInvalidMatchReasons(ap.getType()));
        if (getNTeams() != null && ap.getNTeams() != null && !getNTeams().intersect(ap.getNTeams())){
            reasons.add("Arena accepts nteams="+getNTeamRange()+". you requested "+ap.getNTeamRange());
        }
        if (getTeamSizes() != null && ap.getTeamSizes() != null && !getTeamSizes().intersect(ap.getTeamSizes())){
            reasons.add("Arena accepts teamSize="+getNTeamRange()+". you requested "+ap.getNTeamRange());
        }
        return reasons;
    }

    public boolean valid() {
        return (arenaType != null &&
                (nTeams == null || nTeams.valid()) && (teamSize == null || teamSize.valid()));
    }

    public Collection<String> getInvalidReasons() {
        List<String> reasons = new ArrayList<String>();
        if (arenaType == null) reasons.add("ArenaType is null");
        if (nTeams != null && !nTeams.valid()){
            reasons.add("Min Teams is greater than Max Teams " + nTeams.min + ":" + nTeams.max);}
        if (teamSize != null && !teamSize.valid()){
            reasons.add("Min Team Size is greater than Max Team Size " + teamSize.min + ":" + teamSize.max);}
        return reasons;
    }

    public String getCommand() {
        return cmd != null ? cmd :
                (parent != null ? parent.getCommand() : null);

    }

    public void setRated(boolean rated) {
        this.rated = rated;
    }
    public Boolean isRated(){
        return rated != null ? rated : (parent != null ? parent.isRated() : null);
    }

    public void setSecondsToLoot(Integer i) {
        secondsToLoot=i;
    }

    public Integer getSecondsToLoot() {
        return secondsToLoot != null ? secondsToLoot :
                (parent != null ? parent.getSecondsToLoot() : null);
    }

    public void setSecondsTillMatch(Integer i) {
        secondsTillMatch=i;
    }

    public Integer getSecondsTillMatch() {
        return secondsTillMatch != null ? secondsTillMatch :
                (parent != null ? parent.getSecondsTillMatch() : null);
    }

    public void setTimeBetweenRounds(Integer i) {
        timeBetweenRounds=i;
    }

    public Integer getTimeBetweenRounds() {
        return timeBetweenRounds != null ? timeBetweenRounds :
                (parent != null ? parent.getTimeBetweenRounds() : null);
    }

    public void setDBName(String dbName) {
        this.dbName = dbName;
    }
    public String getDBName(){
        return dbName != null ? dbName :
                (parent != null ? parent.getDBName() : null);
    }

    public String getName() {
        return name != null ? name :
                (parent != null ? parent.getName() : null);
    }
    public void setName(String name) {
        this.name = name;
    }

    public String toPrettyString() {
        return  name+":"+arenaType+",nteams="+getNTeamRange()+",teamSize="+getTeamSizeRange();
    }

    @Override
    public String toString(){
        return  name+":"+arenaType +",nteams="+
                getNTeamRange()+",teamSize="+getTeamSizeRange() +" options=\n"+
                (getTransitionOptions()==null ? "" : getTransitionOptions().getOptionString());
    }

    public boolean isDuelOnly() {
        MatchTransitions tops = getTransitionOptions();
        return tops != null && tops.hasOptionAt(MatchState.DEFAULTS, TransitionOption.DUELONLY);
    }

    public boolean isAlwaysOpen(){
        MatchTransitions tops = getTransitionOptions();
        return tops != null && tops.hasOptionAt(MatchState.DEFAULTS, TransitionOption.ALWAYSOPEN);
    }

    public void setParent(ArenaParams parent) {
        this.parent=parent;
    }

    public ArenaParams getParent() {
        return parent;
    }

    @SuppressWarnings("ConstantConditions")
    public Integer getMinTeamSize() {
        return teamSize != null ? teamSize.min : (parent != null ? parent.getMinTeamSize() : null);
    }

    @SuppressWarnings("ConstantConditions")
    public Integer getMaxTeamSize() {

        return teamSize != null ? teamSize.max : (parent != null ? parent.getMaxTeamSize() : null);
    }

    @SuppressWarnings("ConstantConditions")
    public Integer getMinTeams() {
        return nTeams != null ? nTeams.min : (parent != null ? parent.getMinTeams() : null);
    }

    @SuppressWarnings("ConstantConditions")
    public Integer getMaxTeams() {
        return nTeams != null ? nTeams.max : (parent != null ? parent.getMaxTeams() : null);
    }

    public Integer getMaxPlayers() {
        MinMax nt = getNTeams();
        MinMax ts = getTeamSizes();
        if (nt==null || ts == null)
            return null;
        return nt.max == ArenaSize.MAX || ts.max == ArenaSize.MAX ? ArenaSize.MAX : nt.max * ts.max;
    }

    public Integer getMinPlayers() {
        MinMax nt = getNTeams();
        MinMax ts = getTeamSizes();
        if (nt==null || ts == null)
            return null;
        return nt.min == ArenaSize.MAX || ts.min == ArenaSize.MAX ? ArenaSize.MAX : nt.min * ts.min;
    }

    public void setTeamSize(Integer n) {
        if (n == null){
            teamSize = null;
        } else {
            if (teamSize == null){
                teamSize = new MinMax(n);
            } else {
                teamSize.min = n;
                teamSize.max = n;
            }
        }
    }

    public void setNTeams(MinMax mm) {
        if (mm == null){
            nTeams = null;
        } else {
            if (nTeams == null){
                nTeams = new MinMax(mm);
            } else {
                nTeams.min = mm.min;
                nTeams.max = mm.max;
            }
        }
    }

    /**
     * @return MinMax representing the number of teams
     */
    public MinMax getNTeams(){
        return nTeams != null ? nTeams : (parent != null ? parent.getNTeams() : null);
    }

    /**
     * @return MinMax representing the team sizes
     */
    public MinMax getTeamSizes(){
        return teamSize != null ? teamSize : (parent != null ? parent.getTeamSizes() : null);
    }

    public void setTeamSizes(MinMax mm) {
        if (mm == null){
            teamSize = null;
        } else {
            if (teamSize == null){
                teamSize = new MinMax(mm);
            } else {
                teamSize.min = mm.min;
                teamSize.max = mm.max;
            }
        }
    }

    public void setMinTeamSize(int n) {
        if (teamSize == null){ teamSize = new MinMax(n);}
        else teamSize.min = n;
    }
    public void setMaxTeamSize(int n) {
        if (teamSize == null){ teamSize = new MinMax(n);}
        else teamSize.max = n;
    }
    public void setMinTeams(int n) {
        if (nTeams == null){ nTeams = new MinMax(n);}
        else nTeams.min = n;
    }
    public void setMaxTeams(int n) {
        if (nTeams == null){ nTeams = new MinMax(n);}
        else nTeams.max = n;
    }

    public boolean matchesTeamSize(int i) {
        return teamSize != null ? teamSize.contains(i) : (parent != null && parent.matchesTeamSize(i));
    }

    public boolean hasOptionAt(MatchState state, TransitionOption op) {
        return ( ( getTransitionOptions() != null && getTransitionOptions().hasOptionAt(state, op) ||
                ( parent != null && parent.hasOptionAt(state, op)) ));
    }

    public boolean hasEntranceFee() {
        return hasOptionAt(MatchState.PREREQS,TransitionOption.MONEY);
    }

    public Double getEntranceFee(){
        return getDoubleOption(MatchState.PREREQS, TransitionOption.MONEY);
    }

    public Double getDoubleOption(MatchState state, TransitionOption option){
        MatchTransitions tops = getTransitionOptions();
        if (tops != null){
            Double value = tops.getDoubleOption(state,option);
            if (value != null) {
                return value;
            } else if (parent != null){
                return parent.getDoubleOption(state,option);
            }
        } else if (parent != null){
            return parent.getDoubleOption(state, option);
        }
        return null;
    }
    public boolean hasAnyOption(TransitionOption option) {
        MatchTransitions tops = getTransitionOptions();
        return (tops != null && tops.hasAnyOption(option)) || (parent != null && parent.hasAnyOption(option));
    }

    public List<ItemStack> getWinnerItems() {
        return getGiveItems(MatchState.WINNERS);
    }

    public List<ItemStack> getLoserItems() {
        return getGiveItems(MatchState.LOSERS);
    }

    public List<ItemStack> getGiveItems(MatchState state) {
        MatchTransitions tops = getTransitionOptions();
        return (tops!=null && tops.hasOptionAt(state, TransitionOption.GIVEITEMS)) ?
                tops.getOptions(state).getGiveItems() : (parent != null ? parent.getGiveItems(state) : null);
    }

    public List<PotionEffect> getEffects(MatchState state) {
        MatchTransitions tops = getTransitionOptions();
        return (tops!=null && tops.hasOptionAt(state, TransitionOption.ENCHANTS)) ?
                tops.getOptions(state).getEffects() : (parent != null ? parent.getEffects(state) : null);
    }

    public boolean needsWaitroom() {
        return ( (allTops != null &&
                getTransitionOptions().hasAnyOption(
                        TransitionOption.TELEPORTMAINWAITROOM,TransitionOption.TELEPORTWAITROOM)) ||
                (parent != null && parent.needsWaitroom())
        );
    }

    public boolean needsSpectate() {
        return ( (allTops != null &&
                getTransitionOptions().hasAnyOption(TransitionOption.TELEPORTSPECTATE)) ||
                (parent != null && parent.needsSpectate())
        );
    }

    public boolean needsLobby() {
        return ( (allTops != null &&
                getTransitionOptions().hasAnyOption(
                        TransitionOption.TELEPORTMAINLOBBY,TransitionOption.TELEPORTLOBBY)) ||
                (parent != null && parent.needsLobby())
        );
    }

    public Boolean isWaitroomClosedWhenRunning(){
        return closeWaitroomWhileRunning != null ? closeWaitroomWhileRunning :
                (parent != null ? parent.isWaitroomClosedWhenRunning() : null);
    }

    public void setWaitroomClosedWhileRunning(Boolean value) {
        this.closeWaitroomWhileRunning = value;
    }

    public Boolean isCancelIfNotEnoughPlayers(){
        return cancelIfNotEnoughPlayers != null ? cancelIfNotEnoughPlayers :
                (parent != null ? parent.isCancelIfNotEnoughPlayers() : null);
    }

    public void setCancelIfNotEnoughPlayers(Boolean value) {
        this.cancelIfNotEnoughPlayers = value;
    }
    public String getDisplayName() {
        return this.getName();
    }

    public int getQueueCount() {
        return BattleArena.getBAController().getArenaMatchQueue().getQueueCount(this);
    }

    public void setArenaCooldown(int cooldown) {
        this.arenaCooldown = cooldown;
    }

    public Integer getArenaCooldown() {
        return arenaCooldown != null ? arenaCooldown : (parent != null ? parent.getArenaCooldown(): null);
    }

    public void setAllowedTeamSizeDifference(int difference) {
        this.allowedTeamSizeDifference = difference;
    }

    public Integer getAllowedTeamSizeDifference() {
        return allowedTeamSizeDifference != null ? allowedTeamSizeDifference :
                (parent != null ? parent.getAllowedTeamSizeDifference(): null);
    }

    public Map<Integer, MatchParams> getTeamParams() {
        return teamParams;
    }

    public void setTeamParams(Map<Integer, MatchParams> teamParams) {
        this.teamParams = teamParams;
    }
}

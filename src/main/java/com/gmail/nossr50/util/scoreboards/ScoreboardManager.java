package com.gmail.nossr50.util.scoreboards;

import com.gmail.nossr50.datatypes.database.PlayerStat;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.UserManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.*;

/**
 * Manages the Scoreboards used to display a variety of mcMMO related information to the player
 */
public class ScoreboardManager {
    static final Map<String, ScoreboardWrapper> PLAYER_SCOREBOARDS = new HashMap<>();

    // do not localize; these are internal identifiers
    static final String SIDEBAR_OBJECTIVE = "mcmmo_sidebar";
    static final String POWER_OBJECTIVE = "mcmmo_pwrlvl";

    static final String HEADER_STATS = pluginRef.getLocaleManager().getString("Scoreboard.Header.PlayerStats");
    static final String HEADER_COOLDOWNS = pluginRef.getLocaleManager().getString("Scoreboard.Header.PlayerCooldowns");
    static final String HEADER_RANK = pluginRef.getLocaleManager().getString("Scoreboard.Header.PlayerRank");
    static final String TAG_POWER_LEVEL = pluginRef.getLocaleManager().getString("Scoreboard.Header.PowerLevel");

    static final String POWER_LEVEL = pluginRef.getLocaleManager().getString("Scoreboard.Misc.PowerLevel");

    static final String LABEL_POWER_LEVEL = POWER_LEVEL;
    static final String LABEL_LEVEL = pluginRef.getLocaleManager().getString("Scoreboard.Misc.Level");
    static final String LABEL_CURRENT_XP = pluginRef.getLocaleManager().getString("Scoreboard.Misc.CurrentXP");
    static final String LABEL_REMAINING_XP = pluginRef.getLocaleManager().getString("Scoreboard.Misc.RemainingXP");
    static final String LABEL_ABILITY_COOLDOWN = pluginRef.getLocaleManager().getString("Scoreboard.Misc.Cooldown");
    static final String LABEL_OVERALL = pluginRef.getLocaleManager().getString("Scoreboard.Misc.Overall");

    static final Map<PrimarySkillType, String> skillLabels;
    static final Map<SuperAbilityType, String> abilityLabelsColored;
    static final Map<SuperAbilityType, String> abilityLabelsSkill;
    private static List<String> dirtyPowerLevels = new ArrayList<>();

    /*
     * Initializes the static properties of this class
     */
    static {
        /*
         * We need immutable objects for our ConfigScoreboard's labels
         */
        ImmutableMap.Builder<PrimarySkillType, String> skillLabelBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<SuperAbilityType, String> abilityLabelBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<SuperAbilityType, String> abilityLabelSkillBuilder = ImmutableMap.builder();

        /*
         * Builds the labels for our ScoreBoards
         * Stylizes the targetBoard in a Rainbow Pattern
         * This is off by default
         */
        if (pluginRef.getScoreboardSettings().getUseRainbowSkillStyling()) {
            // Everything but black, gray, gold
            List<ChatColor> colors = Lists.newArrayList(
                    ChatColor.WHITE,
                    ChatColor.YELLOW,
                    ChatColor.LIGHT_PURPLE,
                    ChatColor.RED,
                    ChatColor.AQUA,
                    ChatColor.GREEN,
                    ChatColor.DARK_GRAY,
                    ChatColor.BLUE,
                    ChatColor.DARK_PURPLE,
                    ChatColor.DARK_RED,
                    ChatColor.DARK_AQUA,
                    ChatColor.DARK_GREEN,
                    ChatColor.DARK_BLUE);

            Collections.shuffle(colors, Misc.getRandom());

            int i = 0;
            for (PrimarySkillType type : PrimarySkillType.values()) {
                // Include child skills
                skillLabelBuilder.put(type, getShortenedName(colors.get(i) + type.getName(), false));

                if (type.getSuperAbility() != null) {
                    abilityLabelBuilder.put(type.getSuperAbility(), getShortenedName(colors.get(i) + type.getSuperAbility().getName()));

                    if (type == PrimarySkillType.MINING) {
                        abilityLabelBuilder.put(SuperAbilityType.BLAST_MINING, getShortenedName(colors.get(i) + SuperAbilityType.BLAST_MINING.getName()));
                    }
                }

                if (++i == colors.size()) {
                    i = 0;
                }
            }
        }
        /*
         * Builds the labels for our ScoreBoards
         * Stylizes the targetBoard using our normal color scheme
         */
        else {
            for (PrimarySkillType type : PrimarySkillType.values()) {
                // Include child skills
                skillLabelBuilder.put(type, getShortenedName(ChatColor.GREEN + type.getName()));

                if (type.getSuperAbility() != null) {
                    abilityLabelBuilder.put(type.getSuperAbility(), formatAbility(type.getSuperAbility().getName()));

                    if (type == PrimarySkillType.MINING) {
                        abilityLabelBuilder.put(SuperAbilityType.BLAST_MINING, formatAbility(SuperAbilityType.BLAST_MINING.getName()));
                    }
                }
            }
        }

        for (SuperAbilityType type : SuperAbilityType.values()) {
            abilityLabelSkillBuilder.put(type, formatAbility((type == SuperAbilityType.BLAST_MINING ? ChatColor.BLUE : ChatColor.AQUA), type.getName()));
        }

        skillLabels = skillLabelBuilder.build();
        abilityLabelsColored = abilityLabelBuilder.build();
        abilityLabelsSkill = abilityLabelSkillBuilder.build();
    }

    private static String formatAbility(String abilityName) {
        return formatAbility(ChatColor.AQUA, abilityName);
    }

    private static String formatAbility(ChatColor color, String abilityName) {
        if (pluginRef.getScoreboardSettings().getUseAbilityNamesOverGenerics()) {
            return getShortenedName(color + abilityName);
        } else {
            return color + pluginRef.getLocaleManager().getString("Scoreboard.Misc.Ability");
        }
    }

    private static String getShortenedName(String name) {
        return getShortenedName(name, true);
    }

    private static String getShortenedName(String name, boolean useDots) {
        if (name.length() > 16) {
            name = useDots ? name.substring(0, 14) + ".." : name.substring(0, 16);
        }

        return name;
    }

    // Called by PlayerJoinEvent listener
    public static void setupPlayer(Player player) {
        PLAYER_SCOREBOARDS.put(player.getName(), ScoreboardWrapper.create(player));
        dirtyPowerLevels.add(player.getName());
    }

    // **** Listener call-ins **** //

    // Called by PlayerQuitEvent listener
    public static void teardownPlayer(Player player) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.remove(player.getName());

        if (wrapper != null && wrapper.revertTask != null) {
            wrapper.revertTask.cancel();
        }
    }

    // Called in onDisable()
    public static void teardownAll() {
        ImmutableList<Player> onlinePlayers = ImmutableList.copyOf(pluginRef.getServer().getOnlinePlayers());
        pluginRef.debug("Tearing down scoreboards... (" + onlinePlayers.size() + ")");
        for (Player player : onlinePlayers) {
            teardownPlayer(player);
        }
    }

    // Called by ScoreboardWrapper when its Player logs off and an action tries to be performed
    public static void cleanup(ScoreboardWrapper wrapper) {
        PLAYER_SCOREBOARDS.remove(wrapper.playerName);

        if (wrapper.revertTask != null) {
            wrapper.revertTask.cancel();
        }
    }

    // Called by internal level-up event listener
    public static void handleLevelUp(Player player, PrimarySkillType skill) {
        // Selfboards
        ScoreboardWrapper selfboardWrapper = PLAYER_SCOREBOARDS.get(player.getName());

        if ((selfboardWrapper.isSkillScoreboard() && selfboardWrapper.targetSkill == skill) || (selfboardWrapper.isStatsScoreboard()) && selfboardWrapper.isBoardShown()) {
            selfboardWrapper.doSidebarUpdateSoon();
        }

        // Otherboards
        String playerName = player.getName();

        for (ScoreboardWrapper wrapper : PLAYER_SCOREBOARDS.values()) {
            if (wrapper.isStatsScoreboard() && playerName.equals(wrapper.targetPlayer) && selfboardWrapper.isBoardShown()) {
                selfboardWrapper.doSidebarUpdateSoon();
            }
        }

        if (pluginRef.getScoreboardSettings().getPowerLevelTagsEnabled() && !dirtyPowerLevels.contains(playerName)) {
            dirtyPowerLevels.add(playerName);
        }

        if (pluginRef.getScoreboardSettings().getConfigSectionScoreboardTypes().getConfigSectionSkillBoard().isUseThisBoard()) {
            enablePlayerSkillLevelUpScoreboard(player, skill);
        }
    }

    // Called by internal xp event listener
    public static void handleXp(Player player, PrimarySkillType skill) {
        // Selfboards
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        if (wrapper != null && wrapper.isSkillScoreboard() && wrapper.targetSkill == skill && wrapper.isBoardShown()) {
            wrapper.doSidebarUpdateSoon();
        }
    }

    // Called by internal ability event listeners
    public static void cooldownUpdate(Player player, PrimarySkillType skill) {
        // Selfboards
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        if (wrapper != null && (wrapper.isCooldownScoreboard() || wrapper.isSkillScoreboard() && wrapper.targetSkill == skill) && wrapper.isBoardShown()) {
            wrapper.doSidebarUpdateSoon();
        }
    }

    public static void enablePlayerSkillScoreboard(Player player, PrimarySkillType skill) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSkill(skill);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.SKILL_BOARD));
    }

    // **** Setup methods **** //

    public static void enablePlayerSkillLevelUpScoreboard(Player player, PrimarySkillType skill) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        // Do NOT run if already shown
        if (wrapper.isBoardShown()) {
            return;
        }

        wrapper.setOldScoreboard();
        wrapper.setTypeSkill(skill);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getConfigSectionScoreboardTypes().getConfigSectionSkillBoard().getShowBoardOnPlayerLevelUpTime());
    }

    public static void enablePlayerStatsScoreboard(Player player) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSelfStats();

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.STATS_BOARD));
    }

    public static void enablePlayerInspectScoreboard(Player player, PlayerProfile targetProfile) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeInspectStats(targetProfile);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getConfigSectionScoreboardTypes().getConfigSectionInspectBoard().getDisplayTimeInSeconds());
    }

    public static void enablePlayerCooldownScoreboard(Player player) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeCooldowns();

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.COOLDOWNS_BOARD));
    }

    public static void showPlayerRankScoreboard(Player player, Map<PrimarySkillType, Integer> rank) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeSelfRank();
        wrapper.acceptRankData(rank);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.RANK_BOARD));
    }

    public static void showPlayerRankScoreboardOthers(Player player, String targetName, Map<PrimarySkillType, Integer> rank) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeInspectRank(targetName);
        wrapper.acceptRankData(rank);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.RANK_BOARD));
    }

    public static void showTopScoreboard(Player player, PrimarySkillType skill, int pageNumber, List<PlayerStat> stats) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeTop(skill, pageNumber);
        wrapper.acceptLeaderboardData(stats);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.TOP_BOARD));
    }

    public static void showTopPowerScoreboard(Player player, int pageNumber, List<PlayerStat> stats) {
        ScoreboardWrapper wrapper = PLAYER_SCOREBOARDS.get(player.getName());

        wrapper.setOldScoreboard();
        wrapper.setTypeTopPower(pageNumber);
        wrapper.acceptLeaderboardData(stats);

        changeScoreboard(wrapper, pluginRef.getScoreboardSettings().getScoreboardDisplayTime(SidebarType.TOP_BOARD));
    }

    /**
     * @return false if power levels are disabled
     */
    public static boolean powerLevelHeartbeat() {
        Objective mainObjective = getPowerLevelObjective();

        if (mainObjective == null) {
            return false; // indicates
        }

        for (String playerName : dirtyPowerLevels) {
            McMMOPlayer mcMMOPlayer = UserManager.getPlayer(playerName);

            if (mcMMOPlayer == null) {
                continue;
            }

            Player player = mcMMOPlayer.getPlayer();
            int power = mcMMOPlayer.getPowerLevel();

            mainObjective.getScore(playerName).setScore(power);

            for (ScoreboardWrapper wrapper : PLAYER_SCOREBOARDS.values()) {
                wrapper.updatePowerLevel(player, power);
            }
        }

        dirtyPowerLevels.clear();
        return true;
    }

    // **** Helper methods **** //

    /**
     * Gets or creates the power level objective on the main targetBoard.
     * <p/>
     * If power levels are disabled, the objective is deleted and null is
     * returned.
     *
     * @return the main targetBoard objective, or null if disabled
     */
    public static Objective getPowerLevelObjective() {
        if (!pluginRef.getScoreboardSettings().getPowerLevelTagsEnabled()) {
            Objective objective = pluginRef.getServer().getScoreboardManager().getMainScoreboard().getObjective(POWER_OBJECTIVE);

            if (objective != null) {
                objective.unregister();
                pluginRef.debug("Removed leftover targetBoard objects from Power Level Tags.");
            }

            return null;
        }

        Objective powerObjective = pluginRef.getServer().getScoreboardManager().getMainScoreboard().getObjective(POWER_OBJECTIVE);

        if (powerObjective == null) {
            powerObjective = pluginRef.getServer().getScoreboardManager().getMainScoreboard().registerNewObjective(POWER_OBJECTIVE, "dummy");
            powerObjective.setDisplayName(TAG_POWER_LEVEL);
            powerObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }

        return powerObjective;
    }

    private static void changeScoreboard(ScoreboardWrapper wrapper, int displayTime) {
        if (displayTime == -1) {
            wrapper.showBoardWithNoRevert();
        } else {
            wrapper.showBoardAndScheduleRevert(displayTime * Misc.TICK_CONVERSION_FACTOR);
        }
    }

    public static boolean isBoardShown(String playerName) {
        return PLAYER_SCOREBOARDS.get(playerName).isBoardShown();
    }

    public static void clearBoard(String playerName) {
        PLAYER_SCOREBOARDS.get(playerName).tryRevertBoard();
    }

    public static void keepBoard(String playerName) {
        PLAYER_SCOREBOARDS.get(playerName).cancelRevert();
    }

    public static void setRevertTimer(String playerName, int seconds) {
        PLAYER_SCOREBOARDS.get(playerName).showBoardAndScheduleRevert(seconds * Misc.TICK_CONVERSION_FACTOR);
    }

    public enum SidebarType {
        NONE,
        SKILL_BOARD,
        STATS_BOARD,
        COOLDOWNS_BOARD,
        RANK_BOARD,
        TOP_BOARD
    }
}

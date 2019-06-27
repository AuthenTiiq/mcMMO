package com.gmail.nossr50.runnables.database;

import com.gmail.nossr50.database.DatabaseManager;
import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.UserManager;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

public class FormulaConversionTask extends BukkitRunnable {
    private CommandSender sender;
    private FormulaType previousFormula;

    public FormulaConversionTask(CommandSender sender, FormulaType previousFormula) {
        this.sender = sender;
        this.previousFormula = previousFormula;
    }

    @Override
    public void run() {
        int convertedUsers = 0;
        long startMillis = System.currentTimeMillis();
        for (String playerName : pluginRef.getDatabaseManager().getStoredUsers()) {
            McMMOPlayer mcMMOPlayer = UserManager.getOfflinePlayer(playerName);
            PlayerProfile profile;

            // If the mcMMOPlayer doesn't exist, create a temporary profile and check if it's present in the database. If it's not, abort the process.
            if (mcMMOPlayer == null) {
                profile = pluginRef.getDatabaseManager().loadPlayerProfile(playerName, false);

                if (!profile.isLoaded()) {
                    pluginRef.debug("Profile not loaded.");
                    continue;
                }

                editValues(profile);
                // Since this is a temporary profile, we save it here.
                profile.scheduleAsyncSave();
            } else {
                profile = mcMMOPlayer.getProfile();
                editValues(profile);
            }
            convertedUsers++;
            Misc.printProgress(convertedUsers, DatabaseManager.progressInterval, startMillis);
        }

        sender.sendMessage(pluginRef.getLocaleManager().getString("Commands.mcconvert.Experience.Finish", pluginRef.getConfigManager().getConfigLeveling().getConfigExperienceFormula().toString()));
    }

    private void editValues(PlayerProfile profile) {
        pluginRef.debug("========================================================================");
        pluginRef.debug("Conversion report for " + profile.getPlayerName() + ":");
        for (PrimarySkillType primarySkillType : PrimarySkillType.NON_CHILD_SKILLS) {
            int oldLevel = profile.getSkillLevel(primarySkillType);
            int oldXPLevel = profile.getSkillXpLevel(primarySkillType);
            int totalOldXP = pluginRef.getFormulaManager().calculateTotalExperience(oldLevel, oldXPLevel, previousFormula);

            if (totalOldXP == 0) {
                continue;
            }

            int[] newExperienceValues = pluginRef.getFormulaManager().calculateNewLevel(primarySkillType, (int) Math.floor(totalOldXP / 1.0));
            int newLevel = newExperienceValues[0];
            int newXPlevel = newExperienceValues[1];

            pluginRef.debug("  Skill: " + primarySkillType.toString());

            pluginRef.debug("    OLD:");
            pluginRef.debug("      Level: " + oldLevel);
            pluginRef.debug("      XP " + oldXPLevel);
            pluginRef.debug("      Total XP " + totalOldXP);

            pluginRef.debug("    NEW:");
            pluginRef.debug("      Level " + newLevel);
            pluginRef.debug("      XP " + newXPlevel);
            pluginRef.debug("------------------------------------------------------------------------");

            profile.modifySkill(primarySkillType, newLevel);
            profile.setSkillXpLevel(primarySkillType, newXPlevel);
        }
    }
}

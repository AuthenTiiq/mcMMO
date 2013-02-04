package com.gmail.nossr50.commands.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gmail.nossr50.datatypes.McMMOPlayer;
import com.gmail.nossr50.datatypes.PlayerProfile;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.Users;

public class McrefreshCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PlayerProfile profile;

        switch (args.length) {
        case 0:
            if (!(sender instanceof Player)) {
                return false;
            }

            profile = Users.getPlayer(sender.getName()).getProfile();
            sender.sendMessage(LocaleLoader.getString("Ability.Generic.Refresh"));
            break;

        case 1:
            if (!Permissions.hasPermission(sender, "mcmmo.commands.mcrefresh.others")) {
                sender.sendMessage(command.getPermissionMessage());
            }

            McMMOPlayer mcMMOPlayer = Users.getPlayer(args[0]);

            // If the mcMMOPlayer doesn't exist, create a temporary profile and check if it's present in the database. If it's not, abort the process.
            if (mcMMOPlayer == null) {
                profile = new PlayerProfile(args[0], false);

                if (!profile.isLoaded()) {
                    sender.sendMessage(LocaleLoader.getString("Commands.DoesNotExist"));
                    return true;
                }
            }
            else {
                profile = mcMMOPlayer.getProfile();
                Player player = mcMMOPlayer.getPlayer();

                // Check if the player is online before we try to send them a message.
                if (player.isOnline()) {
                    player.sendMessage(LocaleLoader.getString("Ability.Generic.Refresh"));
                }
            }

            sender.sendMessage(LocaleLoader.getString("Commands.mcrefresh.Success", args[0]));
            break;

        default:
            return false;
        }

        profile.setRecentlyHurt(0);
        profile.resetCooldowns();
        profile.resetToolPrepMode();
        profile.resetAbilityMode();
        return true;
    }
}

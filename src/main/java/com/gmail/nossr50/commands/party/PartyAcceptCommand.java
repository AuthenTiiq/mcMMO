package com.gmail.nossr50.commands.party;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.util.player.UserManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyAcceptCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            Player player = (Player) sender;

            //Check if player profile is loaded
            if (UserManager.getPlayer(player) == null) {
                sender.sendMessage(pluginRef.getLocaleManager().getString("Profile.PendingLoad"));
                return true;
            }

            McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);


            if (!mcMMOPlayer.hasPartyInvite()) {
                sender.sendMessage(pluginRef.getLocaleManager().getString("mcMMO.NoInvites"));
                return true;
            }

            // Changing parties
            if (!pluginRef.getPartyManager().changeOrJoinParty(mcMMOPlayer, mcMMOPlayer.getPartyInvite().getName())) {
                return true;
            }

            pluginRef.getPartyManager().joinInvitedParty(mcMMOPlayer);
            return true;
        }
        sender.sendMessage(pluginRef.getLocaleManager().getString("Commands.Usage.1", "party", "accept"));
        return true;
    }
}

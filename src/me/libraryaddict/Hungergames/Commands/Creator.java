package me.libraryaddict.Hungergames.Commands;

import me.libraryaddict.Hungergames.Managers.TranslationManager;
import me.libraryaddict.Hungergames.Types.HungergamesApi;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Creator implements CommandExecutor {
    public String[] aliases = new String[] { "download" };
    private TranslationManager cm = HungergamesApi.getTranslationManager();
    public String description = "(:";

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        sender.sendMessage(String.format(cm.getCommandCreator(), ":)", ":D"));
        return true;
    }
}

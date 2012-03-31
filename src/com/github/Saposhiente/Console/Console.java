/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.github.Saposhiente.Console;

import java.util.logging.Logger;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Saposhiente
 * TODO: Add support for consoleseption
 */
public class Console extends JavaPlugin {

    Logger log;
    ConsoleListener consoleListener;
    @Override
    public void onEnable(){
        log = this.getLogger();
        consoleListener = new ConsoleListener(/*log*/);
        getServer().getPluginManager().registerEvents(consoleListener, this);
        log.info("Console enabled!");
    }

    @Override
    public void onDisable(){
        log.info("Console disabled.");

    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
	Player player = null;
        boolean console = true;
	if (sender instanceof Player) {
		player = (Player) sender;
                console = false;
	}

        if (cmd.getName().equalsIgnoreCase("do")) { //execute one command
            if (console) {
                try {
                    log.info(consoleListener.doCommand(args, null));
                } catch (SyntaxError ex) {
                    log.warning(ex.getMessage());
                }
            } else {
                try {
                    player.sendMessage(consoleListener.doCommand(args, player));
                } catch (SyntaxError ex) {
                    player.sendMessage(ex.getMessage());
                }
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("c")) { //open console
            if (console) { //TODO
                log.info("Yo dawg, I herd you like consoles, so I put a console in a console so you could use a feature that hasn't been added yet.");
                } else {
                consoleListener.addPlayer(player);
                player.sendMessage("Entering console. Say exit to get out.");
            }
            return true;
        }
        return false;
    }

}

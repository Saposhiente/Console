/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.github.Saposhiente.Console;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 *
 * @author Saposhiente
 */
class Variable {
    public final String value;
    public final boolean readonly;
    public Variable(String v) {
        value = v;
        readonly = false;
    }
    public Variable(String v, boolean ro) {
        value = v;
        readonly = ro;
    }
}
class Environment {
    Map<String, Variable> vars = new HashMap<String, Variable>();
    Map<String, String> aliases = new HashMap<String, String>();
    int EID;
    boolean hasTarget = false;
    int targetShell; //for sudo
    int inputShell; //for security with sudo
    boolean isGhost = false;
    public Environment(int i) {
        EID = i;
        setv("$", String.valueOf(i), true);
    }
    public Environment(int i, int inputS) {
        isGhost = true;
        inputShell = inputS;
        EID = i;
        setv("$", String.valueOf(i), true);
    }
    public String getv(String name) {
        if (vars.containsKey(name)) {
            return vars.get(name).value;
        } else {
            return "";
        }
    }
    public void setv(String name, String value) {
        if (!vars.containsKey(name)||!vars.get(name).readonly) {
            vars.put(name, new Variable(value));
        }
    }
    public void setv(String name, String value, boolean readonly) {
        if (!vars.containsKey(name)||!vars.get(name).readonly) {
            vars.put(name, new Variable(value, readonly));
        }
    }
}
public class ConsoleListener implements Listener {
    private List<Player> consolePlayers = new ArrayList<Player>();
    private List<Environment> consoleEnvironments = new ArrayList<Environment>();
    private List<Player> ghostConsolePlayers = new ArrayList<Player>();
    private List<Environment> ghostConsoleEnvironments = new ArrayList<Environment>();
    /*Logger log;
    ConsoleListener(Logger l) {
    log=l;
    consolePlayers.add(null);
    consoleEnvironments.add(new Environment(0)); //environment 0 = console
    }*/
    public ConsoleListener() {
        consolePlayers.add(null);
        consoleEnvironments.add(new Environment(0)); //environment 0 = console
    }
    public int addPlayer(Player p) { //returns int position to be passed to removePlayer
        int i = consolePlayers.size();
        consolePlayers.add(p);
        consoleEnvironments.add(new Environment(i));
        return i;
    }
    public int addGhostPlayer(Player p, int in) { //returns int position to be passed to removePlayer
        int i = ghostConsolePlayers.size();
        ghostConsolePlayers.add(p);
        ghostConsoleEnvironments.add(new Environment(i, in));
        return i;
    }
    public void removePlayer(int i, Player p) {
        consolePlayers.remove(i);
        consoleEnvironments.remove(i);
        int gi = ghostConsolePlayers.indexOf(p);
        if (gi>-1) {
            ghostConsoleEnvironments.remove(gi);
            ghostConsolePlayers.remove(gi);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        int i = (consolePlayers.indexOf(event.getPlayer()));
        if (i>-1) {
            removePlayer(i, event.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(PlayerChatEvent event) {
        Player p = event.getPlayer();
        int i = consolePlayers.indexOf(p);
        if (i > -1) {
            String m = event.getMessage();
            if (m.charAt(0) != '/') {
                event.setCancelled(true);
                Environment env = consoleEnvironments.get(i);
                if (env.hasTarget) { //sudo
                    if (ghostConsoleEnvironments.size() <= i || ghostConsoleEnvironments.get(i).inputShell != i) {
                        p.sendMessage("Target shell died.");
                        env.hasTarget = false;
                    } else { //no support for sudoing within a sudo because that'd likely be because of a secuity hole
                        try { //no legitimate use for it
                            p.sendMessage(doCommand(m, ghostConsolePlayers.get(i), ghostConsoleEnvironments.get(i)));
                        } catch (SyntaxError ex) {
                            p.sendMessage(ex.getMessage());
                        }
                    }
                } else {
                    try {
                        p.sendMessage(doCommand(event.getMessage(), p, env));
                    } catch (SyntaxError ex) {
                        p.sendMessage(ex.getMessage());
                    }
                }
            }
        }
    }
    public String doCommand(String[] command, Player player) throws SyntaxError {
        //log.info(command.toString());
        return doCommand(command, player, new Environment(-1), new HashSet());
    }
    String doCommand(String command, Player player, Environment env) throws SyntaxError {
        return doCommand(command.split(" "), player, env, new HashSet());
    }
    String doCommand(String[] command, Player player, Environment env, Set<String> aliasesused) throws SyntaxError {
        if (env.aliases.containsKey(command[0])&&!aliasesused.contains(command[0])) {
            aliasesused.add(command[0]); //Prevent infinite recursion
            String[] r = env.aliases.get(command[0]).split(" ");
            String[] temp = new String[(command.length)+(r.length)-1];
            System.arraycopy(r, 0, temp, 0, r.length);
            System.arraycopy(command, 1, temp, r.length, command.length);
            return doCommand(temp, player, env, aliasesused);
        } else { 
            String temp = "";
            boolean isFirst = true;
            for (String s : command) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    temp += " ";
                }
                temp +=s;
            }
            return parse(temp, player, env);
        }
    }
    private String parse(String command, Player player, Environment env) throws SyntaxError {
        /*isSingleQuoted = 1
        isDoubleQuoted = 2
        isBacktick = 3
        isVariable = 4
        isVariableStart = 5
        isParenthesis = 6
        isParenthesisStart = 7
        isArithmetic = 8
        isArithmeticParenthesis = 9
        isVariableBracket = 10*/
        if (command.contains(";")) {
            String temp = "";
            for (String expr:command.split(";")) {
                temp=parse(expr, player, env);
            }
            return temp;
        }
        Stack status = new Stack(); //For nested backtick and doublequotes.
        status.push("0");
        boolean isEscaped = false;
        Stack cmd = new Stack(); 
        cmd.push(new ArrayList()); // Quotes = push a string. Append the whole string to the previous string
        cmd.push(""); //Backtick = push an array list and a string. At each space, put the string in the
        for (char c:command.toCharArray()) {//list and empty the list. Evaluate the list, append the result
            switch (Integer.parseInt((String) status.peek())) {
                case 0: //default
                    if (isEscaped) {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                        isEscaped = false;
                    } else if (c == ' ') { //Add word
                        String word = (String) cmd.pop();
                        ((List<String>) cmd.peek()).add(word);
                        cmd.push("");
                    } else if (c == '\'') {
                        status.push("1");
                        cmd.push("");
                    } else if (c == '"') {
                        status.push("2");
                        cmd.push("");
                    } else if (c == '`') {
                        status.push("3");
                        cmd.push(new ArrayList());
                        cmd.push("");
                    } else if (c == '$') {
                        status.push("5");
                    } else if (c == '\\') {
                        isEscaped = true;
                    } else {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                    } break;
                case 1: //Singlequoted
                    if (c == '\'') {
                        status.pop(); //Pop status and integrate result in nesting operation
                        String temp = (String) cmd.pop();
                        cmd.push(((String) cmd.pop())+temp);
                    } else {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                    }
                    break;
                case 2: //Doublequoted
                    if (isEscaped) {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                        isEscaped = false;
                    } else if (c == '"') {
                        status.pop(); //Pop status and integrate result in nesting operation
                        String temp = (String) cmd.pop();
                        cmd.push(((String) cmd.pop())+temp);
                    } else if (c == '`') {
                        status.push(3);
                        cmd.push(new ArrayList());
                        cmd.push("");
                    } else if (c == '$') {
                        status.push("5");
                    } else if (c == '\\') {
                        isEscaped = true;
                    } else {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                    }
                    break;
                case 3: //Backtick
                    if (isEscaped) {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                        isEscaped = false;
                    } else if (c == ' ') { //Add word
                        String word = (String) cmd.pop();
                        ((List<String>) cmd.peek()).add(word);
                        cmd.push("");
                    } else if (c == '\'') {
                        status.push("1");
                        cmd.push("");
                    } else if (c == '"') {
                        status.push("2");
                        cmd.push("");
                    } else if (c == '`') {
                        status.pop();
                        String word = (String) cmd.pop();
                        List<String> parsedCommand = ((List<String>) cmd.pop());
                        parsedCommand.add(word);
                        cmd.push(((String) cmd.pop())+evaluate(parsedCommand, player, env));
                    } else if (c == '$') {
                        status.push(5);
                    } else if (c == '\\') {
                        isEscaped = true;
                    } else {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                    } break;
                case 4: //Variable
                    if (c ==' ') {
                        status.pop();
                        String name = (String)cmd.pop();
                        if (((String) status.peek()).equals("2")) {//where space doesn't go to next word
                            cmd.push(((String) cmd.pop())+env.getv(name)+" ");
                        } else { //where space goes to next word
                            String word = (((String) cmd.pop())+env.getv(name));
                            ((List<String>) cmd.peek()).add(word);
                            cmd.push("");
                        }
                    } else if (c=='\'') {
                        status.pop(); //concatenate value and go to next state
                        String name = (String)cmd.pop();
                        cmd.push(((String) cmd.pop())+env.getv(name));
                        status.push("1");
                        cmd.push("");
                    } else if (c=='"') {
                        status.pop(); //concatenate value and go to next state
                        String name = (String)cmd.pop();
                        cmd.push(((String) cmd.pop())+env.getv(name));
                        status.push("2");
                        cmd.push("");
                    } else if (c=='`') {
                        status.pop(); //concatenate value and go to next state
                        String name = (String)cmd.pop();
                        cmd.push(((String) cmd.pop())+env.getv(name));
                        status.push("3");
                        cmd.push(new ArrayList());
                        cmd.push("");
                    } else if (c=='$') {
                        status.pop(); //concatenate value and go to next state
                        String name = (String)cmd.pop();
                        cmd.push(((String) cmd.pop())+env.getv(name));
                        status.push("5");
                    } else {
                        char[] temp2 = new char[1]; //Add character
                        temp2[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp2)));
                    } break;
                case 5: //Variable start
                    status.pop();
                    if (c == '(') {
                        status.push("7");
                    } else if (c == '{') {
                        status.push("10");
                        cmd.push("");
                    } else if (c == ' ') {
                        if (((String) status.peek()).equals("2")) {
                            char[] temp2 = new char[2];
                            temp2[0] = '$';
                            temp2[1] = ' ';
                            String word = ((String) cmd.pop())+(new String(temp2));
                        } else {
                            char[] temp2 = new char[1];
                            temp2[0] = '$';
                            String word = ((String) cmd.pop())+(new String(temp2));
                            ((List<String>) cmd.peek()).add(word);
                            cmd.push("");
                        }
                    } else if (c == '$') {
                        if (((String) status.peek()).equals("2")) {
                            cmd.push(((String) cmd.pop())+(env.getv("$")));
                        } else {
                            String word = (((String) cmd.pop())+(env.getv("$")));
                            ((List<String>) cmd.peek()).add(word);
                            cmd.push("");
                        }
                    } else if (c == '`') {
                        if (((String) status.peek()).equals("2")) {
                            char[] temp2 = new char[2];
                            temp2[0] = '$';
                            temp2[1] = ' ';
                            String word = ((String) cmd.pop())+(new String(temp2));
                        } else {
                            char[] temp2 = new char[1];
                            temp2[0] = '$';
                            String word = ((String) cmd.pop())+(new String(temp2));
                            ((List<String>) cmd.peek()).add(word);
                            cmd.push("");
                        }
                        status.push("3");
                        cmd.push(new ArrayList());
                        cmd.push("");
                    } else if (c == '\\') {
                        isEscaped = true;
                        status.push("4");
                        cmd.push("");
                    } else {
                        status.push("4");
                        char[] temp2 = new char[1];
                        temp2[0] = c;
                        cmd.push(new String(temp2));
                    } break;
                case 6: //Parenthesis
                    if (isEscaped) {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                        isEscaped = false;
                    } else if (c == ' ') { //Add word
                        String word = (String) cmd.pop();
                        ((List<String>) cmd.peek()).add(word);
                        cmd.push("");
                    } else if (c == ')') {
                        status.pop();
                        String word = (String) cmd.pop();
                        List<String> parsedCommand = ((List<String>) cmd.pop());
                        parsedCommand.add(word);
                        cmd.push(((String) cmd.pop())+evaluate(parsedCommand, player, env));
                    } else if (c == '\'') {
                        status.push("1");
                        cmd.push("");
                    } else if (c == '"') {
                        status.push("2");
                        cmd.push("");
                    } else if (c == '`') {
                        status.push("3");
                        cmd.push(new ArrayList());
                        cmd.push("");
                    } else if (c == '$') {
                        status.push("5");
                    } else if (c == '\\') {
                        isEscaped = true;
                    } else {
                        char[] temp = new char[1]; //Add character
                        temp[0] = c;
                        cmd.push(((String) cmd.pop())+(new String(temp)));
                    } break;
                case 7: //Parenthesis start
                    if (c == '(') {
                        status.pop();
                        status.push("8");
                    } else {
                        cmd.push(new ArrayList());
                        cmd.push("");
                        if (c == ' ') { //Add word
                            String word = (String) cmd.pop();
                            ((List<String>) cmd.peek()).add(word);
                            cmd.push("");
                        } else if (c == '\'') {
                            status.push("1");
                            cmd.push("");
                        } else if (c == '"') {
                            status.push("2");
                            cmd.push("");
                        } else if (c == '`') {
                            status.push("3");
                            cmd.push(new ArrayList());
                            cmd.push("");
                        } else if (c == '$') {
                            status.push("5");
                        } else if (c == '\\') {
                            isEscaped = true;
                        } else {
                            char[] temp = new char[1]; //Add character
                            temp[0] = c;
                            cmd.push(((String) cmd.pop())+(new String(temp)));
                        }
                    } break;
                case 8: //arithmetic
                    throw new SyntaxError("Arithmetic is not supported yet.");
                case 9: //arithmetic parenthesis
                    throw new SyntaxError("Arithmetic is not supported yet.");
                case 10: //variable bracket
                    throw new SyntaxError("Bracketed paramater expansion is not supported yet.");
            }
        } //end for
        while (!(((String)status.peek()).equals("0"))) { //attempt to resolve unfinished business
            switch (Integer.parseInt((String) status.peek())) {
                case 1:
                    throw new SyntaxError("Incomplete single quote");
                case 2:
                    throw new SyntaxError("Incomplete double quote");
                case 3:
                    throw new SyntaxError("Incomplete backtick");
                case 4:
                    status.pop();
                    String name = (String) cmd.pop();
                    if (((String) status.peek()).equals("2")) {
                        throw new SyntaxError("Incomplete double quote");
                    } else { //where space goes to next word
                        String word = (((String) cmd.pop()) + env.getv(name));
                        ((List<String>) cmd.peek()).add(word);
                        cmd.push("");
                    } break;
                case 5:
                    status.pop();
                    if (((String) status.peek()).equals("2")) {
                        throw new SyntaxError("Incomplete double quote");
                    } else {
                        char[] temp2 = new char[1];
                        temp2[0] = '$';
                        String word = ((String) cmd.pop()) + (new String(temp2));
                        ((List<String>) cmd.peek()).add(word);
                        cmd.push("");
                    } break;
                case 6:
                case 7:
                case 8:
                case 9:
                    throw new SyntaxError("Incomplete parenthesis");
                case 10:
                    throw new SyntaxError("Incomplete bracket");
            }
        }
        String word = (String) cmd.pop(); //Add word
        List<String> parsedCommand = (List<String>)cmd.pop();
        parsedCommand.add(word);
        return evaluate(parsedCommand, player, env);

    }
    String evaluate(List<String> parsedCommand, Player player, Environment env) throws SyntaxError {
        //TODO: The builtin commands, filesystem modification
        String cmd = parsedCommand.get(0);
        if (cmd.contains("=")) {
            String[] split = cmd.split("=",2);
            env.setv(split[0], split[1]);
            return evaluate(parsedCommand.subList(1, parsedCommand.size()), player, env);
        } else if (cmd.equals("sudo")) {
            if (parsedCommand.size()<2) { throw new SyntaxError("Sudo: Insufficient arguments."); }
            String[] dashArgs;
            if (parsedCommand.get(1).charAt(0)=='-') {
                dashArgs = parsedCommand.get(1).split("-")[1].split("");
            } else {
                dashArgs = new String[0];
            }
            boolean s = false;
            boolean u = false;
            for (String str:dashArgs) {
                if (str.equals("s")) {
                    s = true;
                }
                if (str.equals("u")) {
                    u = true;
                    if (parsedCommand.size()<3) { throw new SyntaxError("Sudo: Insufficient arguments."); }
                }
            }
            if (u) { //player=null means player=console
                if (player==null||player.hasPermission("Console.sudo."+parsedCommand.get(2))) {
                    if (s) { //Open a shell as the specified player
                        if (player==null) {
                            return "Yo dawg, I herd you like consoles, so I put a console in a console so you could use a feature that hasn't been added yet.";
                        } else {
                            Player target = (Bukkit.getServer().getPlayer(parsedCommand.get(2)));
                            if (target == null) {
                                return "You cannot sudo as offline users/Please recheck your typing.";
                            } else {
                                int i = addGhostPlayer(target, env.EID);
                                env.targetShell = i;
                                env.hasTarget = true;
                                return "Switching to ghost shell " + i + ". Please sudo responsibly.";
                            }
                        }
                    } else {
                        Player target = (Bukkit.getServer().getPlayer(parsedCommand.get(2)));
                        if (target == null) {
                            return "You cannot sudo as offline users/Please recheck your typing.";
                        } else {
                            int size = parsedCommand.size();
                            String[] command = new String[size - 3];
                            int i = 3; //far too complicated, but .toArray returns Object[]
                            while (i < size) { //which can't be cast to String[]
                                command[i - 3] = parsedCommand.get(i);
                                i++;
                            }
                            return doCommand(command, target);
                        }

                    }
                } else {
                    return "You do not have permission to sudo as that user. This incident will be reported"; //To Santa Claus.
                }
            } else if (player == null || player.hasPermission("Console.sudoconsole")) {
                if (s) { //Open a shell as the specified player
                    if (player == null) {
                        return "Yo dawg, I herd you like consoles, so I put a console in a console so you could use a feature that hasn't been added yet.";
                    } else {
                        Player target = null;
                        int i = addGhostPlayer(target, env.EID);
                        env.targetShell = i;
                        env.hasTarget = true;
                        return "Switching to ghost shell " + i + ". Please sudo responsibly.";
                    }
                } else {
                    Player target = null;
                    int size=parsedCommand.size();
                    String[] command = new String[size-1];
                    int i=1; //far too complicated, but .toArray returns Object[]
                    while (i<size) { //which can't be cast to String[]
                        command[i-1]=parsedCommand.get(i);
                        i++;
                    }
                    return doCommand(command, target);
                }
            } else {
                return "You do not have permission to sudo as root. This incident will be reported"; //To Santa Claus.
            }
        } else if (cmd.equals("exit")) {
            if (env.isGhost) {
                ghostConsoleEnvironments.remove(env.EID);
                ghostConsolePlayers.remove(env.EID);
                return "Returning to encasing shell.";
            } else {
                removePlayer(env.EID, player);
                return "Exiting console.";
            }
        } else if (cmd.equals("broadcast")) { //Broadcast arbitrary message. Handy for messaging as "God", faking player join/leave notices, etc.
            if (player==null||player.hasPermission("Console.broadcast")) {
                String m = parsedCommand.get(1);
                for (String s:parsedCommand.subList(2, parsedCommand.size())) {
                    m=m+" "+s; //TODO: Colors!
                }
                Bukkit.getServer().broadcastMessage(m);
                return "";
            } else {
                return "You don't have permission to broadcast.";
            }
        } else if (cmd.equals("tell")) {//Send arbitrary message to player. Handy for making people think that something happened, when meanwhile everyone else thinks they're crazy
            if (player==null||player.hasPermission("Console.broadcast")) {
                if (parsedCommand.size()<2) { throw new SyntaxError("You must specify a player."); }
                Player target = (Bukkit.getServer().getPlayer(parsedCommand.get(1)));
                if (target == null) { return "Player not found."; }
                String m = parsedCommand.get(2);
                for (String s:parsedCommand.subList(3, parsedCommand.size())) {
                    m=m+" "+s;
                }
                target.sendMessage(m);
                return "Message sent.";
            } else {
                return "You don't have permission to tell.";
            }
        } else if (cmd.equals("echo")) {//Prints result to you only.
            String m = parsedCommand.get(1);
            for (String s : parsedCommand.subList(2, parsedCommand.size())) {
                m = m + " " + s;
            }
            return m;
        } else if (player == null) {
            return "Execution of arbitrary console commands not yet implemented.";
        } else { //ship the parsed command off as a normal command with a /
            String m = "/"+parsedCommand.get(0); // commands starting with / aren't caught to prevent looping
                for (String s:parsedCommand.subList(1, parsedCommand.size())) {
                    m=m+" "+s;
                }
            Bukkit.getServer().getPluginManager().callEvent(new PlayerChatEvent(player, m));
            //DOES NOT WORK have to find the right event
            return "";
        }
    }
}

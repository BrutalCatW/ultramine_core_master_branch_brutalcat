package org.ultramine.commands.basic;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import static net.minecraft.util.EnumChatFormatting.*;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.ForgeHooks;

import org.apache.commons.lang3.StringUtils;
import org.ultramine.commands.Command;
import org.ultramine.commands.CommandContext;
import org.ultramine.commands.IExtendedCommand;
import org.ultramine.core.service.InjectService;
import org.ultramine.server.Teleporter;
import org.ultramine.server.util.BasicTypeParser;
import org.ultramine.core.permissions.Permissions;

public class VanillaCommands
{
	@InjectService private static Permissions perms;

	@Command(
			name = "difficulty",
			group = "vanilla",
			permissions = {"command.vanilla.difficulty"},
			syntax = {
					"<list peaceful easy normal hard % difficulty>",
					"<world> <list peaceful easy normal hard % difficulty>"
			}
	)
	public static void difficulty(CommandContext ctx)
	{
		WorldServer world = ctx.contains("world") ? ctx.get("world").asWorld() : ctx.getSenderAsPlayer().getServerForPlayer();
		EnumDifficulty difficulty = BasicTypeParser.parseDifficulty(ctx.get("difficulty").asString());
		if(difficulty == null) ctx.throwBadUsage();
		ctx.notifyAdmins("command.difficulty.success", world.getWorldInfo().getWorldName(),
				new ChatComponentTranslation(world.difficultySetting.getDifficultyResourceKey()),
				new ChatComponentTranslation(difficulty.getDifficultyResourceKey()));
		MinecraftServer server = MinecraftServer.getServer();
		if(server.isSinglePlayer())
			server.func_147139_a(difficulty);
		else
			world.difficultySetting = difficulty;
	}
	
	private static void sendMessage(ICommandSender from, ICommandSender to, String message)
	{
		IChatComponent msg = ForgeHooks.newChatWithLinks(message);
		msg.getChatStyle().setColor(BasicTypeParser.parseColor(perms.getMeta(from, "textcolor")));
		
		from.addChatMessage(new ChatComponentTranslation("command.msg.display.outgoing", to.func_145748_c_(), msg).setChatStyle(new ChatStyle().setColor(GOLD)));
		to.addChatMessage(new ChatComponentTranslation("command.msg.display.incoming", from.func_145748_c_(), msg).setChatStyle(new ChatStyle().setColor(GOLD)));
		
		if(from instanceof EntityPlayerMP) ((EntityPlayerMP)from).getData().core().setLastMessagedPlayer(to.getCommandSenderName());
		if(to instanceof EntityPlayerMP) ((EntityPlayerMP)to).getData().core().setLastMessagedPlayer(from.getCommandSenderName());
	}
	
	@Command(
			name = "msg",
			aliases={"tell", "t", "w"},
			group = "vanilla",
			permissions = {"command.vanilla.msg"},
			syntax = {"<player> <%msg>..."}
	)
	public static void msg(CommandContext ctx)
	{
		ICommandSender to = ctx.get("player").asString().equalsIgnoreCase("server") ? MinecraftServer.getServer() : ctx.get("player").asPlayer();
		sendMessage(ctx.getSender(), to, ctx.get("msg").asString());
	}
	
	@Command(
			name = "reply",
			aliases={"r"},
			group = "vanilla",
			permissions = {"command.vanilla.reply"},
			syntax = {"<%msg>..."}
	)
	public static void reply(CommandContext ctx)
	{
		String name = ctx.getSenderAsPlayer().getData().core().getLastMessagedPlayer();
		ctx.check(name != null, "command.reply.fail");
		ICommandSender to = name.equalsIgnoreCase("server") ? MinecraftServer.getServer() : MinecraftServer.getServer().getConfigurationManager().func_152612_a(name);
		sendMessage(ctx.getSender(), to, ctx.get("msg").asString());
	}
	
	@Command(
			name = "weather",
			group = "vanilla",
			permissions = {"command.vanilla.weather"},
			syntax = {
					"[clear sun rain thunder]",
					"[clear sun rain thunder] <nextchange>",
					"<world> [clear sun rain thunder]",
					"<world> [clear sun rain thunder] <nextchange>"
			}
	)
	public static void weather(CommandContext ctx)
	{
		WorldServer world = ctx.contains("world") ? ctx.get("world").asWorld() : ctx.getSenderAsPlayer().getServerForPlayer();
		WorldInfo wi = world.getWorldInfo();
		int nextchange = ctx.contains("nextchange") ? ctx.get("nextchange").asInt(1, 1000000) * 20 : (300 + world.rand.nextInt(600)) * 20;
		
		wi.setRainTime(nextchange);
		wi.setThunderTime(nextchange);
		if(ctx.getAction().equals("clear") || ctx.getAction().equals("sun"))
		{
			wi.setRaining(false);
			wi.setThundering(false);
			ctx.sendMessage("commands.weather.clear");
		}
		else if(ctx.getAction().equals("rain"))
		{
			wi.setRaining(true);
			ctx.sendMessage("commands.weather.rain");
		}
		else if(ctx.getAction().equals("thunder"))
		{
			wi.setRaining(true);
			wi.setThundering(true);
			ctx.sendMessage("commands.weather.thunder");
		}
	}
	
	@Command(
			name = "time",
			group = "vanilla",
			permissions = {"command.vanilla.time"},
			syntax = {
					"<list day night %worldtime>",
					"[set add] <list day night %worldtime>",
					"<world> <list day night %worldtime>",
					"<world> [set add] <list day night %worldtime>",
			}
	)
	public static void time(CommandContext ctx)
	{
		WorldServer world = ctx.contains("world") ? ctx.get("world").asWorld() : ctx.getSenderAsPlayer().getServerForPlayer();
		String timeS = ctx.get("worldtime").asString();
		int time = timeS.equals("day") ? 1000 : timeS.equals("night") ? 13000 : ctx.get("worldtime").asInt(0);
		long curTime = world.getWorldInfo().getWorldTime();
		if(ctx.getAction().isEmpty() || ctx.getAction().equals("set"))
		{
			world.setWorldTime(curTime - (curTime % 24000) + 24000 + time);
			ctx.sendMessage("commands.time.set", time);
		}
		else if(ctx.getAction().equals("add"))
		{
			world.setWorldTime(curTime + time);
			ctx.sendMessage("commands.time.added", time);
		}
	}
	
	@Command(
			name = "gamerule",
			group = "vanilla",
			permissions = {"command.vanilla.gamerule"},
			syntax = {
					"",
					"<key>",
					"<world> <key>",
					"<key> <value>",
					"<world> <key> <value>"
			}
	)
	public static void gamerule(CommandContext ctx)
	{
		GameRules rules = (ctx.contains("world") ? ctx.get("world").asWorld() : ctx.getSenderAsPlayer().getServerForPlayer()).getGameRules();
		if(ctx.contains("value"))
		{
			String key = ctx.get("key").asString();
			if(rules.hasRule(key))
			{
				rules.setOrCreateGameRule(key, ctx.get("value").asString());
				ctx.sendMessage("commands.gamerule.success");
			}
			else
			{
				ctx.sendMessage("commands.gamerule.norule", key);
			}
		}
		else if(ctx.contains("key"))
		{
			String key = ctx.get("key").asString();
			if(rules.hasRule(key))
				ctx.sendMessage("%s = %s", key, rules.getGameRuleStringValue(key));
			else
				ctx.sendMessage("commands.gamerule.norule", key);
		}
		else
		{
			ctx.sendMessage(StringUtils.join(rules.getRules(), ", "));
		}
	}
}

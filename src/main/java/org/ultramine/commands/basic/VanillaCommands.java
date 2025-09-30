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
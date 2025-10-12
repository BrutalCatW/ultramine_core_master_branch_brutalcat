package org.ultramine.commands.basic;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import org.ultramine.commands.Command;
import org.ultramine.commands.CommandContext;

import java.util.List;

/**
 * Команда для тестирования производительности системы поиска игроков.
 * Сравнивает старый метод (полный перебор) с новым (пространственное индексирование).
 */
public class PlayerSearchBenchmark {

	@Command(
		name = "playerbench",
		aliases = {"pbench"},
		group = "ultramine",
		permissions = {"ultramine.playerbench"},
		syntax = {"<radius>"},
		isUsableFromServer = false
	)
	public static void benchmarkPlayerSearch(CommandContext ctx) {
		EntityPlayerMP player = (EntityPlayerMP) ctx.getSender();
		World world = player.worldObj;

		int radius = 64; // Радиус по умолчанию
		if (ctx.getArgs().length > 0) {
			try {
				radius = ctx.get(0).asInt(1);
			} catch (Exception e) {
				player.addChatMessage(new ChatComponentText("§cОшибка: неверный радиус. Используется значение по умолчанию: 64"));
			}
		}

		double x = player.posX;
		double y = player.posY;
		double z = player.posZ;

		player.addChatMessage(new ChatComponentText("§e=== Тест производительности поиска игроков ==="));
		player.addChatMessage(new ChatComponentText("§7Радиус: §f" + radius + " §7блоков"));
		player.addChatMessage(new ChatComponentText("§7Позиция: §f" + String.format("%.1f, %.1f, %.1f", x, y, z)));
		player.addChatMessage(new ChatComponentText("§7Всего игроков в мире: §f" + world.playerEntities.size()));

		// ====== СТАРЫЙ МЕТОД (Vanilla) ======
		long startOld = System.nanoTime();
		List<EntityPlayer> oldResult = findPlayersOldWay(world, x, y, z, radius);
		long endOld = System.nanoTime();
		long oldTime = endOld - startOld;

		// ====== НОВЫЙ МЕТОД (Optimized) ======
		long startNew = System.nanoTime();
		List<EntityPlayer> newResult = world.getPlayersInRadius(x, y, z, radius);
		long endNew = System.nanoTime();
		long newTime = endNew - startNew;

		// ====== РЕЗУЛЬТАТЫ ======
		player.addChatMessage(new ChatComponentText(""));
		player.addChatMessage(new ChatComponentText("§a▪ §7Старый метод (полный перебор):"));
		player.addChatMessage(new ChatComponentText("  §7Найдено игроков: §f" + oldResult.size()));
		player.addChatMessage(new ChatComponentText("  §7Время: §f" + formatTime(oldTime) + " §7(" + oldTime + " ns)"));

		player.addChatMessage(new ChatComponentText(""));
		player.addChatMessage(new ChatComponentText("§b▪ §7Новый метод (индексирование):"));
		player.addChatMessage(new ChatComponentText("  §7Найдено игроков: §f" + newResult.size()));
		player.addChatMessage(new ChatComponentText("  §7Время: §f" + formatTime(newTime) + " §7(" + newTime + " ns)"));

		player.addChatMessage(new ChatComponentText(""));
		if (newTime < oldTime) {
			double speedup = (double) oldTime / newTime;
			player.addChatMessage(new ChatComponentText("§2✓ §aУскорение: §f" + String.format("%.2fx", speedup) + " §a(быстрее на §f" + String.format("%.1f%%", (1 - 1/speedup) * 100) + "§a)"));
		} else if (newTime > oldTime) {
			double slowdown = (double) newTime / oldTime;
			player.addChatMessage(new ChatComponentText("§c✗ §cЗамедление: §f" + String.format("%.2fx", slowdown)));
		} else {
			player.addChatMessage(new ChatComponentText("§e= §eРезультаты одинаковые"));
		}

		// Проверка корректности
		if (oldResult.size() != newResult.size()) {
			player.addChatMessage(new ChatComponentText(""));
			player.addChatMessage(new ChatComponentText("§c⚠ ПРЕДУПРЕЖДЕНИЕ: §7Количество найденных игроков отличается!"));
			player.addChatMessage(new ChatComponentText("§7Это может указывать на ошибку в реализации."));
		}
	}

	/**
	 * Старый метод поиска игроков (полный перебор всех игроков в мире).
	 * Эмулирует vanilla поведение для сравнения производительности.
	 */
	@SuppressWarnings("unchecked")
	private static List<EntityPlayer> findPlayersOldWay(World world, double x, double y, double z, double radius) {
		List<EntityPlayer> result = new java.util.ArrayList<EntityPlayer>();
		double radiusSq = radius * radius;

		// Перебираем всех игроков в мире (O(N))
		for (int i = 0; i < world.playerEntities.size(); i++) {
			EntityPlayer player = (EntityPlayer) world.playerEntities.get(i);
			double dx = player.posX - x;
			double dy = player.posY - y;
			double dz = player.posZ - z;
			double distSq = dx * dx + dy * dy + dz * dz;

			if (distSq <= radiusSq) {
				result.add(player);
			}
		}

		return result;
	}

	/**
	 * Форматирует время в человекочитаемый вид.
	 */
	private static String formatTime(long nanos) {
		if (nanos < 1000) {
			return nanos + " ns";
		} else if (nanos < 1000000) {
			return String.format("%.2f μs", nanos / 1000.0);
		} else if (nanos < 1000000000) {
			return String.format("%.2f ms", nanos / 1000000.0);
		} else {
			return String.format("%.2f s", nanos / 1000000000.0);
		}
	}
}

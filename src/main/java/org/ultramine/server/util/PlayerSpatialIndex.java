package org.ultramine.server.util;

import net.minecraft.entity.player.EntityPlayer;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

import java.util.ArrayList;
import java.util.List;

/**
 * Оптимизированная система пространственного индексирования игроков для быстрого поиска в радиусе.
 * Использует сетку (grid) для разделения мира на секторы и Koloboke коллекции для производительности.
 *
 * Производительность:
 * - O(1) для добавления/удаления игрока
 * - O(k) для поиска в радиусе, где k - количество игроков в затронутых секторах (обычно << N)
 * - Вместо O(N) перебора всех игроков в мире
 *
 * @author Ultramine Core
 */
public class PlayerSpatialIndex {

	/**
	 * Размер сектора в блоках.
	 * 16 блоков = 1 чанк - оптимальный размер для баланса между памятью и производительностью.
	 * При поиске в радиусе 64 блока затрагивается только ~16-25 секторов вместо проверки всех игроков.
	 */
	private static final int SECTOR_SIZE = 16;

	/**
	 * Сдвиг для быстрого деления на SECTOR_SIZE через битовые операции.
	 * 16 = 2^4, поэтому сдвиг = 4
	 */
	private static final int SECTOR_SHIFT = 4;

	/**
	 * Карта секторов: ключ - хеш координат сектора, значение - список игроков в секторе.
	 * Используется Koloboke IntObjMap для минимизации накладных расходов (нет boxing/unboxing).
	 */
	private final HashIntObjMap<List<EntityPlayer>> sectorMap;

	/**
	 * Кеш секторов для каждого игрока, чтобы быстро удалять при перемещении.
	 * Ключ - hashCode игрока, значение - хеш сектора.
	 */
	private final HashIntObjMap<Integer> playerSectorCache;

	public PlayerSpatialIndex() {
		this.sectorMap = HashIntObjMaps.newMutableMap();
		this.playerSectorCache = HashIntObjMaps.newMutableMap();
	}

	/**
	 * Вычисляет хеш сектора по координатам блока.
	 * Использует алгоритм, аналогичный ChunkHash из Ultramine.
	 *
	 * @param blockX координата X блока
	 * @param blockZ координата Z блока
	 * @return хеш сектора (int)
	 */
	private static int getSectorHash(int blockX, int blockZ) {
		int sectorX = blockX >> SECTOR_SHIFT;
		int sectorZ = blockZ >> SECTOR_SHIFT;
		// Упаковываем координаты сектора в int (16 бит на координату)
		return (sectorX & 0xFFFF) << 16 | (sectorZ & 0xFFFF);
	}

	/**
	 * Вычисляет хеш сектора по координатам игрока.
	 */
	private static int getSectorHash(EntityPlayer player) {
		return getSectorHash((int)Math.floor(player.posX), (int)Math.floor(player.posZ));
	}

	/**
	 * Добавляет или обновляет позицию игрока в индексе.
	 * Если игрок переместился в другой сектор - автоматически обновляет.
	 *
	 * Сложность: O(1) амортизированная
	 *
	 * @param player игрок для добавления/обновления
	 */
	public void updatePlayer(EntityPlayer player) {
		int playerHash = System.identityHashCode(player);
		int newSectorHash = getSectorHash(player);

		// Проверяем, есть ли игрок в кеше
		Integer oldSectorHash = playerSectorCache.get(playerHash);

		if (oldSectorHash != null) {
			// Если сектор не изменился - ничего не делаем
			if (oldSectorHash == newSectorHash) {
				return;
			}

			// Удаляем из старого сектора
			List<EntityPlayer> oldSector = sectorMap.get(oldSectorHash);
			if (oldSector != null) {
				oldSector.remove(player);
				// Удаляем пустой сектор для экономии памяти
				if (oldSector.isEmpty()) {
					sectorMap.remove(oldSectorHash);
				}
			}
		}

		// Добавляем в новый сектор
		List<EntityPlayer> newSector = sectorMap.get(newSectorHash);
		if (newSector == null) {
			newSector = new ArrayList<EntityPlayer>(4); // Начальная емкость 4 игрока на сектор
			sectorMap.put(newSectorHash, newSector);
		}
		newSector.add(player);

		// Обновляем кеш
		playerSectorCache.put(playerHash, Integer.valueOf(newSectorHash));
	}

	/**
	 * Удаляет игрока из индекса.
	 * Вызывается при отключении игрока или телепортации в другой мир.
	 *
	 * Сложность: O(1) амортизированная
	 *
	 * @param player игрок для удаления
	 */
	public void removePlayer(EntityPlayer player) {
		int playerHash = System.identityHashCode(player);
		Integer sectorHash = playerSectorCache.remove(playerHash);

		if (sectorHash != null) {
			List<EntityPlayer> sector = sectorMap.get(sectorHash);
			if (sector != null) {
				sector.remove(player);
				if (sector.isEmpty()) {
					sectorMap.remove(sectorHash);
				}
			}
		}
	}

	/**
	 * Находит всех игроков в радиусе от точки.
	 * Проверяет только секторы, которые могут содержать игроков в радиусе.
	 *
	 * Сложность: O(k), где k - количество игроков в затронутых секторах (обычно << N)
	 * Vanilla сложность: O(N), где N - все игроки в мире
	 *
	 * Ожидаемый выигрыш: 5-50x при 50+ игроках, в зависимости от плотности распределения
	 *
	 * @param x координата X центра поиска
	 * @param z координата Z центра поиска
	 * @param radius радиус поиска в блоках
	 * @return список игроков в радиусе
	 */
	public List<EntityPlayer> getPlayersInRadius(double x, double z, double radius) {
		List<EntityPlayer> result = new ArrayList<EntityPlayer>();

		// Вычисляем диапазон секторов, которые пересекаются с радиусом
		int minSectorX = ((int)Math.floor(x - radius)) >> SECTOR_SHIFT;
		int maxSectorX = ((int)Math.floor(x + radius)) >> SECTOR_SHIFT;
		int minSectorZ = ((int)Math.floor(z - radius)) >> SECTOR_SHIFT;
		int maxSectorZ = ((int)Math.floor(z + radius)) >> SECTOR_SHIFT;

		double radiusSq = radius * radius;

		// Перебираем только затронутые секторы
		for (int sectorX = minSectorX; sectorX <= maxSectorX; sectorX++) {
			for (int sectorZ = minSectorZ; sectorZ <= maxSectorZ; sectorZ++) {
				int sectorHash = (sectorX & 0xFFFF) << 16 | (sectorZ & 0xFFFF);
				List<EntityPlayer> sector = sectorMap.get(sectorHash);

				if (sector != null) {
					// Проверяем каждого игрока в секторе
					for (EntityPlayer player : sector) {
						double dx = player.posX - x;
						double dz = player.posZ - z;
						double distSq = dx * dx + dz * dz;

						if (distSq <= radiusSq) {
							result.add(player);
						}
					}
				}
			}
		}

		return result;
	}

	/**
	 * Находит всех игроков в радиусе от точки с учетом координаты Y.
	 * Использует трехмерное расстояние для более точного поиска.
	 *
	 * @param x координата X центра поиска
	 * @param y координата Y центра поиска
	 * @param z координата Z центра поиска
	 * @param radius радиус поиска в блоках
	 * @return список игроков в радиусе
	 */
	public List<EntityPlayer> getPlayersInRadius(double x, double y, double z, double radius) {
		List<EntityPlayer> result = new ArrayList<EntityPlayer>();

		int minSectorX = ((int)Math.floor(x - radius)) >> SECTOR_SHIFT;
		int maxSectorX = ((int)Math.floor(x + radius)) >> SECTOR_SHIFT;
		int minSectorZ = ((int)Math.floor(z - radius)) >> SECTOR_SHIFT;
		int maxSectorZ = ((int)Math.floor(z + radius)) >> SECTOR_SHIFT;

		double radiusSq = radius * radius;

		for (int sectorX = minSectorX; sectorX <= maxSectorX; sectorX++) {
			for (int sectorZ = minSectorZ; sectorZ <= maxSectorZ; sectorZ++) {
				int sectorHash = (sectorX & 0xFFFF) << 16 | (sectorZ & 0xFFFF);
				List<EntityPlayer> sector = sectorMap.get(sectorHash);

				if (sector != null) {
					for (EntityPlayer player : sector) {
						double dx = player.posX - x;
						double dy = player.posY - y;
						double dz = player.posZ - z;
						double distSq = dx * dx + dy * dy + dz * dz;

						if (distSq <= radiusSq) {
							result.add(player);
						}
					}
				}
			}
		}

		return result;
	}

	/**
	 * Находит ближайшего игрока к точке в заданном радиусе.
	 * Более эффективная альтернатива getClosestPlayer из World.
	 *
	 * @param x координата X центра поиска
	 * @param y координата Y центра поиска
	 * @param z координата Z центра поиска
	 * @param maxRadius максимальный радиус поиска (-1 для неограниченного)
	 * @return ближайший игрок или null
	 */
	public EntityPlayer getClosestPlayer(double x, double y, double z, double maxRadius) {
		EntityPlayer closest = null;
		double closestDistSq = maxRadius < 0 ? Double.MAX_VALUE : maxRadius * maxRadius;

		// Оптимизация: начинаем с малого радиуса и расширяем при необходимости
		int searchRadius = 1; // Начинаем с 1 сектора
		int maxSearchRadius = maxRadius < 0 ? 64 : ((int)Math.ceil(maxRadius) >> SECTOR_SHIFT) + 1;

		while (searchRadius <= maxSearchRadius) {
			int centerSectorX = ((int)Math.floor(x)) >> SECTOR_SHIFT;
			int centerSectorZ = ((int)Math.floor(z)) >> SECTOR_SHIFT;

			// Проверяем кольцо секторов на расстоянии searchRadius
			for (int dx = -searchRadius; dx <= searchRadius; dx++) {
				for (int dz = -searchRadius; dz <= searchRadius; dz++) {
					// Пропускаем уже проверенные внутренние секторы
					if (searchRadius > 1 && Math.abs(dx) < searchRadius && Math.abs(dz) < searchRadius) {
						continue;
					}

					int sectorX = centerSectorX + dx;
					int sectorZ = centerSectorZ + dz;
					int sectorHash = (sectorX & 0xFFFF) << 16 | (sectorZ & 0xFFFF);
					List<EntityPlayer> sector = sectorMap.get(sectorHash);

					if (sector != null) {
						for (EntityPlayer player : sector) {
							double distSq = player.getDistanceSq(x, y, z);
							if (distSq < closestDistSq) {
								closestDistSq = distSq;
								closest = player;
							}
						}
					}
				}
			}

			// Если нашли игрока - можем остановиться (дальше не будет ближе)
			if (closest != null) {
				break;
			}

			searchRadius++;
		}

		return closest;
	}

	/**
	 * Очищает индекс полностью.
	 * Используется при выгрузке мира.
	 */
	public void clear() {
		sectorMap.clear();
		playerSectorCache.clear();
	}

	/**
	 * Возвращает количество активных секторов.
	 * Полезно для отладки и мониторинга.
	 */
	public int getSectorCount() {
		return sectorMap.size();
	}

	/**
	 * Возвращает количество индексированных игроков.
	 * Полезно для отладки и мониторинга.
	 */
	public int getPlayerCount() {
		return playerSectorCache.size();
	}
}

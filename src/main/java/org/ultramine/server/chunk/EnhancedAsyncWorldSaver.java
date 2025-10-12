package org.ultramine.server.chunk;

import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Улучшенный асинхронный менеджер сохранения данных мира
 * Дополняет существующую систему ThreadedFileIOBase улучшенной пакетной обработкой
 */
public class EnhancedAsyncWorldSaver
{
	private static final Logger logger = LogManager.getLogger();
	private static EnhancedAsyncWorldSaver instance;

	// Пул потоков для сохранения
	private final ExecutorService saveExecutor;

	// Статистика
	private final AtomicInteger chunksSaved = new AtomicInteger(0);
	private final AtomicLong totalSaveTime = new AtomicLong(0);
	private final AtomicInteger activeSaves = new AtomicInteger(0);

	// Настройки
	private volatile boolean enabled = true;
	private volatile int batchSize = 10; // Количество чанков сохраняемых за раз
	private volatile long batchDelayMs = 50; // Задержка между батчами

	private EnhancedAsyncWorldSaver()
	{
		// Создаем пул потоков для сохранения
		this.saveExecutor = new ThreadPoolExecutor(
			1, // Core pool size
			3, // Maximum pool size
			60L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(),
			r -> {
				Thread thread = new Thread(r, "Enhanced Async World Saver");
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 1);
				return thread;
			}
		);
	}

	public static EnhancedAsyncWorldSaver instance()
	{
		if (instance == null)
		{
			synchronized (EnhancedAsyncWorldSaver.class)
			{
				if (instance == null)
				{
					instance = new EnhancedAsyncWorldSaver();
				}
			}
		}
		return instance;
	}

	/**
	 * Асинхронно сохраняет чанк с низким приоритетом
	 *
	 * @param world Мир
	 * @param chunk Чанк для сохранения
	 * @return Future для отслеживания выполнения
	 */
	public Future<Boolean> saveChunkAsync(WorldServer world, Chunk chunk)
	{
		if (!enabled || chunk == null || world == null)
		{
			return CompletableFuture.completedFuture(false);
		}

		return saveExecutor.submit(() -> {
			activeSaves.incrementAndGet();
			long startTime = System.currentTimeMillis();
			boolean success = false;

			try
			{
				// Проверяем, нужно ли сохранять
				if (chunk.needsSaving(false))
				{
					try
					{
						chunk.lastSaveTime = world.getTotalWorldTime();
						world.theChunkProviderServer.currentChunkLoader.saveChunk(world, chunk);
						chunk.isModified = false;
						chunk.postSave();

						chunksSaved.incrementAndGet();
						success = true;
					}
					catch (IOException | MinecraftException e)
					{
						logger.error("Failed to save chunk [{}, {}] in dimension {}",
							chunk.xPosition, chunk.zPosition, world.provider.dimensionId, e);
					}
				}

				return success;
			}
			finally
			{
				long elapsed = System.currentTimeMillis() - startTime;
				totalSaveTime.addAndGet(elapsed);
				activeSaves.decrementAndGet();
			}
		});
	}

	/**
	 * Асинхронно сохраняет батч чанков
	 *
	 * @param world Мир
	 * @param chunks Массив чанков для сохранения
	 * @return Future для отслеживания выполнения
	 */
	public Future<Integer> saveChunkBatch(WorldServer world, Chunk[] chunks)
	{
		if (!enabled || chunks == null || chunks.length == 0)
		{
			return CompletableFuture.completedFuture(0);
		}

		return saveExecutor.submit(() -> {
			int saved = 0;

			for (Chunk chunk : chunks)
			{
				if (chunk != null && chunk.needsSaving(false))
				{
					activeSaves.incrementAndGet();
					long startTime = System.currentTimeMillis();

					try
					{
						chunk.lastSaveTime = world.getTotalWorldTime();
						world.theChunkProviderServer.currentChunkLoader.saveChunk(world, chunk);
						chunk.isModified = false;
						chunk.postSave();

						saved++;
						chunksSaved.incrementAndGet();

						// Небольшая задержка между сохранениями в батче
						if (batchDelayMs > 0 && saved < chunks.length)
						{
							Thread.sleep(batchDelayMs);
						}
					}
					catch (IOException | MinecraftException e)
					{
						logger.error("Failed to save chunk [{}, {}] in dimension {} (batch mode)",
							chunk.xPosition, chunk.zPosition, world.provider.dimensionId, e);
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						break;
					}
					finally
					{
						long elapsed = System.currentTimeMillis() - startTime;
						totalSaveTime.addAndGet(elapsed);
						activeSaves.decrementAndGet();
					}
				}
			}

			return saved;
		});
	}

	/**
	 * Асинхронно сохраняет все данные мира (координаты, уровень и т.д.)
	 *
	 * @param world Мир
	 * @param saveHandler Обработчик сохранения
	 * @return Future для отслеживания выполнения
	 */
	public Future<Boolean> saveWorldDataAsync(WorldServer world, ISaveHandler saveHandler)
	{
		if (!enabled || world == null || saveHandler == null)
		{
			return CompletableFuture.completedFuture(false);
		}

		return saveExecutor.submit(() -> {
			try
			{
				saveHandler.saveWorldInfoWithPlayer(world.getWorldInfo(), world.func_73046_m().getConfigurationManager().getHostPlayerData());
				saveHandler.flush();
				return true;
			}
			catch (Exception e)
			{
				logger.error("Failed to save world data for dimension {}", world.provider.dimensionId, e);
				return false;
			}
		});
	}

	/**
	 * Ожидает завершения всех активных операций сохранения
	 *
	 * @param timeoutMs Максимальное время ожидания в миллисекундах
	 * @return true если все операции завершены, false если вышло время
	 */
	public boolean awaitCompletion(long timeoutMs)
	{
		long startTime = System.currentTimeMillis();
		while (activeSaves.get() > 0)
		{
			if (System.currentTimeMillis() - startTime > timeoutMs)
			{
				return false;
			}
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	/**
	 * Включает или выключает улучшенное асинхронное сохранение
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		logger.info("Enhanced async world saver {}", enabled ? "enabled" : "disabled");
	}

	/**
	 * Устанавливает размер батча для сохранения
	 */
	public void setBatchSize(int batchSize)
	{
		this.batchSize = Math.max(1, Math.min(100, batchSize));
	}

	/**
	 * Устанавливает задержку между сохранениями в батче (мс)
	 */
	public void setBatchDelay(long delayMs)
	{
		this.batchDelayMs = Math.max(0, Math.min(1000, delayMs));
	}

	/**
	 * Возвращает статистику сохранения
	 */
	public SaveStats getStats()
	{
		return new SaveStats(
			chunksSaved.get(),
			activeSaves.get(),
			totalSaveTime.get()
		);
	}

	/**
	 * Сбрасывает статистику
	 */
	public void resetStats()
	{
		chunksSaved.set(0);
		totalSaveTime.set(0);
	}

	/**
	 * Корректно останавливает менеджер сохранения
	 */
	public void shutdown()
	{
		logger.info("Shutting down enhanced async world saver...");
		enabled = false;

		// Ждем завершения текущих операций
		awaitCompletion(10000);

		saveExecutor.shutdown();
		try
		{
			if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS))
			{
				saveExecutor.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			saveExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}

		logger.info("Enhanced async world saver shut down. Stats: {} chunks saved, {} ms total",
			chunksSaved.get(), totalSaveTime.get());
	}

	/**
	 * Класс для хранения статистики сохранения
	 */
	public static class SaveStats
	{
		public final int totalChunksSaved;
		public final int activeSaveOperations;
		public final long totalSaveTimeMs;

		public SaveStats(int totalChunksSaved, int activeSaveOperations, long totalSaveTimeMs)
		{
			this.totalChunksSaved = totalChunksSaved;
			this.activeSaveOperations = activeSaveOperations;
			this.totalSaveTimeMs = totalSaveTimeMs;
		}

		public double getAverageSaveTimeMs()
		{
			return totalChunksSaved > 0 ? (double) totalSaveTimeMs / totalChunksSaved : 0.0;
		}

		@Override
		public String toString()
		{
			return String.format("SaveStats{chunks=%d, active=%d, totalTime=%dms, avgTime=%.2fms}",
				totalChunksSaved, activeSaveOperations, totalSaveTimeMs, getAverageSaveTimeMs());
		}
	}
}

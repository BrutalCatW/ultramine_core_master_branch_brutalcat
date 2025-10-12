package org.ultramine.server.chunk;

import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Асинхронный движок расчета освещения для минимизации лагов при обновлении света
 */
public class AsyncLightingEngine
{
	private static final Logger logger = LogManager.getLogger();
	private static AsyncLightingEngine instance;

	// Пул потоков для асинхронного расчета освещения
	private final ExecutorService lightingExecutor;

	// Очередь задач на обновление освещения
	private final BlockingQueue<LightingTask> lightingQueue;

	// Максимальный размер очереди
	private static final int MAX_QUEUE_SIZE = 8192;

	// Счетчик активных задач
	private final AtomicInteger activeTasks = new AtomicInteger(0);

	// Флаг включения/выключения асинхронного освещения
	private volatile boolean enabled = true;

	private AsyncLightingEngine()
	{
		// Создаем пул потоков с приоритетом ниже обычного
		this.lightingExecutor = new ThreadPoolExecutor(
			1, // Core pool size
			2, // Maximum pool size
			60L, TimeUnit.SECONDS, // Keep alive time
			new LinkedBlockingQueue<>(),
			r -> {
				Thread thread = new Thread(r, "Async Lighting Thread");
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 1);
				return thread;
			}
		);

		this.lightingQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

		// Запускаем обработчик очереди
		startQueueProcessor();
	}

	public static AsyncLightingEngine instance()
	{
		if (instance == null)
		{
			synchronized (AsyncLightingEngine.class)
			{
				if (instance == null)
				{
					instance = new AsyncLightingEngine();
				}
			}
		}
		return instance;
	}

	/**
	 * Запускает обработку очереди освещения
	 */
	private void startQueueProcessor()
	{
		lightingExecutor.submit(() -> {
			while (!Thread.currentThread().isInterrupted())
			{
				try
				{
					LightingTask task = lightingQueue.poll(100, TimeUnit.MILLISECONDS);
					if (task != null)
					{
						processLightingTask(task);
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
				catch (Exception e)
				{
					logger.error("Error processing lighting task", e);
				}
			}
		});
	}

	/**
	 * Добавляет задачу на обновление освещения в очередь
	 *
	 * @param world Мир
	 * @param type Тип освещения (Sky или Block)
	 * @param x Координата X
	 * @param y Координата Y
	 * @param z Координата Z
	 * @return true если задача добавлена, false если очередь переполнена
	 */
	public boolean scheduleLightUpdate(World world, EnumSkyBlock type, int x, int y, int z)
	{
		if (!enabled || world.isRemote)
		{
			return false;
		}

		// Проверяем, не переполнена ли очередь
		if (lightingQueue.size() >= MAX_QUEUE_SIZE)
		{
			// Очередь переполнена, выполняем синхронно
			return false;
		}

		LightingTask task = new LightingTask(world, type, x, y, z);
		return lightingQueue.offer(task);
	}

	/**
	 * Обрабатывает задачу освещения асинхронно
	 */
	private void processLightingTask(LightingTask task)
	{
		activeTasks.incrementAndGet();
		try
		{
			// Проверяем, что чанк все еще загружен
			if (!task.world.doChunksNearChunkExist(task.x, task.y, task.z, 17))
			{
				return;
			}

			// Выполняем расчет освещения
			// Это безопасно делать асинхронно, так как мы только читаем данные чанков
			// и обновляем значения освещения, которые имеют собственную синхронизацию
			task.world.updateLightByType(task.type, task.x, task.y, task.z);
		}
		catch (Exception e)
		{
			logger.error("Error updating light at [{}, {}, {}]", task.x, task.y, task.z, e);
		}
		finally
		{
			activeTasks.decrementAndGet();
		}
	}

	/**
	 * Ожидает завершения всех активных задач освещения
	 *
	 * @param timeoutMs Максимальное время ожидания в миллисекундах
	 * @return true если все задачи завершены, false если вышло время
	 */
	public boolean awaitCompletion(long timeoutMs)
	{
		long startTime = System.currentTimeMillis();
		while (activeTasks.get() > 0 || !lightingQueue.isEmpty())
		{
			if (System.currentTimeMillis() - startTime > timeoutMs)
			{
				return false;
			}
			try
			{
				Thread.sleep(10);
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
	 * Включает или выключает асинхронное освещение
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		logger.info("Async lighting engine {}", enabled ? "enabled" : "disabled");
	}

	/**
	 * Проверяет, включено ли асинхронное освещение
	 */
	public boolean isEnabled()
	{
		return enabled;
	}

	/**
	 * Возвращает текущий размер очереди
	 */
	public int getQueueSize()
	{
		return lightingQueue.size();
	}

	/**
	 * Возвращает количество активных задач
	 */
	public int getActiveTasks()
	{
		return activeTasks.get();
	}

	/**
	 * Корректно останавливает движок освещения
	 */
	public void shutdown()
	{
		logger.info("Shutting down async lighting engine...");
		enabled = false;

		// Ждем завершения текущих задач
		awaitCompletion(5000);

		lightingExecutor.shutdown();
		try
		{
			if (!lightingExecutor.awaitTermination(5, TimeUnit.SECONDS))
			{
				lightingExecutor.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			lightingExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}

		logger.info("Async lighting engine shut down");
	}

	/**
	 * Внутренний класс для представления задачи освещения
	 */
	private static class LightingTask
	{
		final World world;
		final EnumSkyBlock type;
		final int x, y, z;
		final long timestamp;

		LightingTask(World world, EnumSkyBlock type, int x, int y, int z)
		{
			this.world = world;
			this.type = type;
			this.x = x;
			this.y = y;
			this.z = z;
			this.timestamp = System.currentTimeMillis();
		}
	}
}

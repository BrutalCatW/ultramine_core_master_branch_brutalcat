package org.ultramine.server.chunk;

import gnu.trove.TCollections;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.ultramine.core.service.InjectService;
import org.ultramine.server.WorldConstants;
import org.ultramine.server.util.BlockFace;
import org.ultramine.server.util.ChunkCoordComparator;
import org.ultramine.server.util.TIntArrayListImpl;

import com.google.common.collect.Queues;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.openhft.koloboke.collect.IntCursor;
import net.openhft.koloboke.collect.set.IntSet;
import net.openhft.koloboke.collect.set.hash.HashIntSets;

/**
 * Enhanced ChunkSendManager with comprehensive debugging and optimization features.
 *
 * Key improvements:
 * - Detailed logging controlled by -Dultramine.chunk.send.debug=true
 * - Performance metrics tracking (sent/cancelled chunks, compression time, sort operations)
 * - Periodic statistics logging every 60 seconds
 * - Optimized sort algorithm (skips sorting if player hasn't moved significantly)
 * - Improved adaptive rate adjustment
 * - JMX MBean support for real-time monitoring
 *
 * @author Ultramine
 */
public class ChunkSendManager implements ChunkSendManagerMBean
{
	private static final Logger log = LogManager.getLogger();
	private static final boolean DEBUG = Boolean.getBoolean("ultramine.chunk.send.debug");
	private static final ExecutorService executor = Executors.newFixedThreadPool(1);
	@InjectService private static AntiXRayService<Object> antiXRayService;
	private static final double MIN_RATE = 0.2d;
	
	private final EntityPlayerMP player;
	private PlayerManager manager;
	private BlockFace lastFace;
	private int lastViewDistance;
	
	private final TIntArrayListImpl toSend = new TIntArrayListImpl(441);
	private final TIntIntMap sending = TCollections.synchronizedMap(new TIntIntHashMap());
	private final TIntSet sendingStage2 = TCollections.synchronizedSet(new TIntHashSet());
	private final IntSet sent = HashIntSets.newMutableSet();
	private final Queue<ChunkIdStruct> toUpdate = Queues.newConcurrentLinkedQueue();
	private final AtomicInteger sendingQueueSize = new AtomicInteger();
	private final Object lock = new Object();
	
	private int lastQueueSize;
	private double rate = 0;
	private int intervalCounter = 1;
	private boolean sentLastTick = false;
	private int sendIndexCounter;
	private boolean sortSendQueueLater;
	private int lastSortChunkX;
	private int lastSortChunkZ;
	private int sortSkipCount = 0;
	private static final int SORT_POSITION_THRESHOLD = 3; // Sort only if moved > 3 chunks

	// Performance metrics
	private final AtomicLong totalChunksSent = new AtomicLong();
	private final AtomicLong totalChunksCancelled = new AtomicLong();
	private final AtomicLong totalSortOperations = new AtomicLong();
	private final AtomicLong totalCompressionTime = new AtomicLong();
	private final AtomicLong compressionCount = new AtomicLong();
	private long lastLogTime = 0;
	private static final long LOG_INTERVAL_MS = 60000; // Log stats every 60 seconds
	
	public ChunkSendManager(EntityPlayerMP player)
	{
		this.player = player;
	}
	
	public int getViewDistance()
	{
		return Math.min(manager == null ? 10 : manager.getWorldServer().getViewDistance(), player.getRenderDistance());
	}
	
	private void sortSendQueue()
	{
		sortSendQueueLater = true;
	}

	private void doSortSendQueue()
	{
		if(!sortSendQueueLater)
			return;

		int cx = MathHelper.floor_double(player.posX) >> 4;
		int cz = MathHelper.floor_double(player.posZ) >> 4;

		// Optimization: skip sorting if player hasn't moved significantly
		int deltaX = Math.abs(cx - lastSortChunkX);
		int deltaZ = Math.abs(cz - lastSortChunkZ);

		if(deltaX < SORT_POSITION_THRESHOLD && deltaZ < SORT_POSITION_THRESHOLD)
		{
			sortSkipCount++;
			if(DEBUG)
			{
				log.log(Level.DEBUG, "[ChunkSend] Player {}: Skipped sort (moved only {},{} chunks), skip count: {}",
					player.getCommandSenderName(), deltaX, deltaZ, sortSkipCount);
			}
			// Don't reset sortSendQueueLater yet, will sort when threshold exceeded
			return;
		}

		sortSendQueueLater = false;
		long startTime = DEBUG ? System.nanoTime() : 0;

		toSend.backSort(ChunkCoordComparator.get(lastFace = BlockFace.yawToFace(player.rotationYaw), cx, cz));
		lastSortChunkX = cx;
		lastSortChunkZ = cz;

		totalSortOperations.incrementAndGet();

		if(DEBUG)
		{
			long elapsed = System.nanoTime() - startTime;
			log.log(Level.DEBUG, "[ChunkSend] Player {}: Sorted queue of {} chunks in {} us, direction: {}, skipped {} times before",
				player.getCommandSenderName(), toSend.size(), elapsed / 1000, lastFace, sortSkipCount);
		}
		sortSkipCount = 0;
	}
	
	private void checkDistance()
	{
		int curView = getViewDistance();
		if(curView != lastViewDistance)
		{
			int cx = MathHelper.floor_double(player.posX) >> 4;
			int cz = MathHelper.floor_double(player.posZ) >> 4;
			
			if(curView < lastViewDistance)
			{
				for(TIntIterator it = toSend.iterator(); it.hasNext();)
				{
					int key = it.next();
					if(!overlaps(cx, cz, ChunkHash.keyToX(key), ChunkHash.keyToZ(key), curView))
						it.remove();
				}
				
				for(int key : sending.keys())
				{
					if(!overlaps(cx, cz, ChunkHash.keyToX(key), ChunkHash.keyToZ(key), curView))
					{
						cancelSending(key);
					}
				}
				
				for(IntCursor it = sent.cursor(); it.moveNext();)
				{
					int key = it.elem();
					if(!overlaps(cx, cz, ChunkHash.keyToX(key), ChunkHash.keyToZ(key), curView))
					{
						PlayerManager.PlayerInstance pi = manager.getOrCreateChunkWatcher(ChunkHash.keyToX(key), ChunkHash.keyToZ(key), false);
						if(pi != null) pi.removePlayer(player);
						it.remove();
					}
				}
			}
			else
			{
				for (int x = cx - curView; x <= cx + curView; ++x)
				{
					for (int z = cz - curView; z <= cz + curView; ++z)
					{
						int key = ChunkHash.chunkToKey(x, z);
						if(!toSend.contains(key) && !sent.contains(key) && !sending.containsKey(key))
						{
							toSend.add(key);
						}
					}
				}
			}
			
			lastViewDistance = curView;
			sortSendQueue();
		}
	}
	
	public void addTo(PlayerManager manager)
	{
		if(this.manager != null) throw new IllegalStateException("PlayerManager already set");
		this.manager = manager;
		
		player.managedPosX = player.posX;
		player.managedPosZ = player.posZ;
		
		int cx = MathHelper.floor_double(player.posX) >> 4;
		int cz = MathHelper.floor_double(player.posZ) >> 4;
		int viewRadius = lastViewDistance = getViewDistance();
		
		for (int x = cx - viewRadius; x <= cx + viewRadius; ++x)
		{
			for (int z = cz - viewRadius; z <= cz + viewRadius; ++z)
			{
				toSend.add(ChunkHash.chunkToKey(x, z));
			}
		}
		
		sortSendQueue();
		
		if(rate == 0)
			rate = Math.max(manager.getWorldServer().getConfig().chunkLoading.maxSendRate/2, 1);
		sendChunks(Math.max(1, (int)rate));
	}
	
	public void removeFrom(PlayerManager manager)
	{
		if(this.manager == null) return;
		if(this.manager != manager) throw new IllegalStateException();
		
		toSend.clear();
		
		for(int key : sending.keys())
		{
			cancelSending(key);
		}
		
		for(IntCursor it = sent.cursor(); it.moveNext();)
		{
			int key = it.elem();
			PlayerManager.PlayerInstance pi = manager.getOrCreateChunkWatcher(ChunkHash.keyToX(key), ChunkHash.keyToZ(key), false);
			if (pi != null) pi.removePlayer(player);
		}
		sent.clear();
		
		this.manager = null;
	}
	
	public void stopSending()
	{
		removeFrom(manager);
	}
	
	public void update()
	{
		if(manager == null)
			return;

		// Periodic statistics logging
		logStatistics();

		updatePlayerPertinentChunks();
		if(!toSend.isEmpty())
		{
			int queueSize = sendingQueueSize.get();
			int maxRate = manager.getWorldServer().getConfig().chunkLoading.maxSendRate;
			int maxQueueSize = maxRate*2;
			
			if(sentLastTick)
			{
				// Optimized adaptive rate adjustment
				double targetRate = maxRate;
				double adjustment;

				if(queueSize == 0)
				{
					// Queue empty - increase rapidly
					adjustment = 0.14;
				}
				else if(queueSize < maxRate / 2)
				{
					// Queue small - increase moderately
					adjustment = 0.10;
				}
				else if(queueSize < maxRate)
				{
					// Queue acceptable - increase slowly
					adjustment = 0.05;
				}
				else if(queueSize > maxQueueSize)
				{
					// Queue too large - decrease rapidly
					adjustment = -0.20;
				}
				else if(queueSize > maxRate && queueSize > lastQueueSize)
				{
					// Queue growing - decrease moderately
					adjustment = -0.10;
				}
				else
				{
					// Queue stable - small adjustment
					adjustment = (maxRate - queueSize) * 0.01;
				}

				rate += adjustment;

				if(rate < MIN_RATE)
					rate = MIN_RATE;
				else if(rate > maxRate)
					rate = maxRate;

				if(DEBUG && adjustment != 0)
				{
					log.log(Level.DEBUG, "[ChunkSend] Player {}: Rate adjusted by {:.2f} to {:.2f} (queue: {}/{})",
						player.getCommandSenderName(), adjustment, rate, queueSize, maxRate);
				}
			}
			
			if(queueSize <= maxQueueSize)
			{
				lastQueueSize = queueSize;
				sentLastTick = true;
			
				if(rate >= 1)
				{
					sendChunks((int)Math.round(rate));
				}
				else
				{
					int interval = Math.max(1, (int)(1/rate));
					if(intervalCounter++ >= interval)
					{
						intervalCounter = 1;
						sendChunks(1);
					}
				}
				
			}
		}
		else
		{
			sentLastTick = false;
		}
		
		for(ChunkIdStruct chunkId; (chunkId = toUpdate.poll()) != null;)
		{
			Chunk chunk = chunkId.chunk;
			int key = ChunkHash.chunkToKey(chunk.xPosition, chunk.zPosition);
			
			if(sending.get(key) == chunkId.id)
			{
				sending.remove(key);
				sendingStage2.remove(key);
				try
				{
					manager.getOrCreateChunkWatcher(chunk.xPosition, chunk.zPosition, true).addPlayer(player);
				}
				catch (IllegalStateException e)
				{
					// in some cases chunk may be sent 2 times. It's not a big problem, I think. Just ignore it
					log.debug(e);
					continue;
				}
				
				@SuppressWarnings("unchecked")
				List<TileEntity> tes = new ArrayList<TileEntity>(chunk.chunkTileEntityMap.values());
				for(TileEntity te : tes)
				{
					if(!te.isInvalid())
					{
						Packet packet = te.getDescriptionPacket();
						if (packet != null)
							player.playerNetServerHandler.sendPacket(packet);
					}
				}
				
				manager.getWorldServer().getEntityTracker().func_85172_a(player, chunk);
				MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(chunk.getChunkCoordIntPair(), player));

				sent.add(key);
				totalChunksSent.incrementAndGet();

				if(DEBUG)
				{
					log.log(Level.DEBUG, "[ChunkSend] Player {}: Successfully sent chunk ({}, {}), total sent: {}",
						player.getCommandSenderName(), chunk.xPosition, chunk.zPosition, totalChunksSent.get());
				}
			}
			else
			{
				PlayerManager.PlayerInstance pi = ((WorldServer)chunk.worldObj).getPlayerManager().getOrCreateChunkWatcher(chunk.xPosition, chunk.zPosition, false);
				if (pi == null)
					((WorldServer)chunk.worldObj).theChunkProviderServer.unbindChunk(chunk);
			}
		}
	}

	private void sendChunks(int count)
	{
		doSortSendQueue();
		count = Math.min(count, toSend.size());
		for(int i = 0; i < count; i++)
		{
			int key = toSend.removeAt(toSend.size() - 1);
			int curID = ++sendIndexCounter;
			sending.put(key, curID);
			sendingQueueSize.incrementAndGet();
			int ncx = ChunkHash.keyToX(key);
			int ncz = ChunkHash.keyToZ(key);
			manager.getWorldServer().theChunkProviderServer.loadAsyncWithRadius(ncx, ncz, 1, new ChunkLoadCallback(curID));
		}
	}
	
	private void updatePlayerPertinentChunks()
	{
		checkDistance();

		int view = getViewDistance();
		double d0 = player.managedPosX - player.posX;
		double d1 = player.managedPosZ - player.posZ;
		double square = d0 * d0 + d1 * d1;
		
		if (square >= 4 * view * view)
		{
			int cx = MathHelper.floor_double(player.posX) >> 4;
			int cz = MathHelper.floor_double(player.posZ) >> 4;
			int lastX = MathHelper.floor_double(player.managedPosX) >> 4;
			int lastZ = MathHelper.floor_double(player.managedPosZ) >> 4;
			int movX = cx - lastX;
			int movZ = cz - lastZ;

			if (movX != 0 || movZ != 0)
			{
				for (int x = cx - view; x <= cx + view; ++x)
				{
					for (int z = cz - view; z <= cz + view; ++z)
					{
						if (!overlaps(x, z, lastX, lastZ, view))
						{
							toSend.add(ChunkHash.chunkToKey(x, z));
						}

						if (!overlaps(x - movX, z - movZ, cx, cz, view))
						{
							int key = ChunkHash.chunkToKey(x - movX, z - movZ);
							if(!toSend.remove(key))
							{
								if(sent.contains(key))
								{
									PlayerManager.PlayerInstance pi = manager.getOrCreateChunkWatcher(x - movX, z - movZ, false);
									if(pi != null) pi.removePlayer(player);
									sent.removeInt(key);
								}
								else
								{
									cancelSending(key);
								}
							}
						}
					}
				}

				sortSendQueue();
				player.managedPosX = player.posX;
				player.managedPosZ = player.posZ;
			}
		}

		if(BlockFace.yawToFace(player.rotationYaw) != lastFace)
			sortSendQueue();
	}
	
	private boolean overlaps(int x, int z, int lastX, int lastZ, int radius)
	{
		int movX = x - lastX;
		int movZ = z - lastZ;
		return (movX >= -radius && movX <= radius) && (movZ >= -radius && movZ <= radius);
	}
	
	private void cancelSending(int key)
	{
		synchronized(lock)
		{
			sending.remove(key);
			if(sendingStage2.remove(key))
			{
				player.playerNetServerHandler.netManager.scheduleOutboundPacket(S21PacketChunkData.makeForUnload(ChunkHash.keyToX(key), ChunkHash.keyToZ(key)));
				totalChunksCancelled.incrementAndGet();

				if(DEBUG)
				{
					log.log(Level.DEBUG, "[ChunkSend] Player {}: Cancelled chunk ({}, {})",
						player.getCommandSenderName(), ChunkHash.keyToX(key), ChunkHash.keyToZ(key));
				}
			}
		}
	}

	private void logStatistics()
	{
		long currentTime = System.currentTimeMillis();
		if(currentTime - lastLogTime > LOG_INTERVAL_MS)
		{
			lastLogTime = currentTime;

			long avgCompressionTime = compressionCount.get() > 0 ?
				totalCompressionTime.get() / compressionCount.get() : 0;

			log.log(Level.INFO, "[ChunkSend] Player {}: Stats - Sent: {}, Cancelled: {}, Sorts: {}, Avg compression: {} us, " +
				"Queues[toSend: {}, sending: {}, sent: {}], Rate: {:.2f}",
				player.getCommandSenderName(),
				totalChunksSent.get(),
				totalChunksCancelled.get(),
				totalSortOperations.get(),
				avgCompressionTime / 1000,
				toSend.size(),
				sending.size(),
				sent.size(),
				rate);
		}
	}

	public String getDebugInfo()
	{
		return String.format("Player: %s, toSend: %d, sending: %d, sent: %d, rate: %.2f, " +
			"totalSent: %d, totalCancelled: %d, sortOps: %d",
			player.getCommandSenderName(), toSend.size(), sending.size(), sent.size(),
			rate, totalChunksSent.get(), totalChunksCancelled.get(), totalSortOperations.get());
	}
	
	public double getRate()
	{
		return rate;
	}

	// JMX monitoring methods
	public long getTotalChunksSent()
	{
		return totalChunksSent.get();
	}

	public long getTotalChunksCancelled()
	{
		return totalChunksCancelled.get();
	}

	public long getTotalSortOperations()
	{
		return totalSortOperations.get();
	}

	public long getAverageCompressionTimeMicros()
	{
		long count = compressionCount.get();
		return count > 0 ? totalCompressionTime.get() / count / 1000 : 0;
	}

	public int getQueueToSendSize()
	{
		return toSend.size();
	}

	public int getQueueSendingSize()
	{
		return sending.size();
	}

	public int getQueueSentSize()
	{
		return sent.size();
	}

	public int getSendingQueueSize()
	{
		return sendingQueueSize.get();
	}

	public String getPlayerName()
	{
		return player.getCommandSenderName();
	}

	public void resetStatistics()
	{
		totalChunksSent.set(0);
		totalChunksCancelled.set(0);
		totalSortOperations.set(0);
		totalCompressionTime.set(0);
		compressionCount.set(0);
		sortSkipCount = 0;
		log.log(Level.INFO, "[ChunkSend] Player {}: Statistics reset", player.getCommandSenderName());
	}
	
	private static class ChunkIdStruct
	{
		public final Chunk chunk;
		public final int id;
		
		public ChunkIdStruct(Chunk chunk, int id)
		{
			this.chunk = chunk;
			this.id = id;
		}
	}
	
	private class ChunkLoadCallback implements IChunkLoadCallback
	{
		private final int id;
		
		public ChunkLoadCallback(int id)
		{
			this.id = id;
		}
		
		@Override
		public void onChunkLoaded(Chunk chunk)
		{
			int key = ChunkHash.chunkToKey(chunk.xPosition, chunk.zPosition);
			if(sending.get(key) != id)
			{
				sendingQueueSize.decrementAndGet();
				return;
			}
			
			if(chunk.isTerrainPopulated)
			{
				chunk.func_150804_b(true);
				chunk.setBindState(ChunkBindState.PLAYER);
				executor.execute(new CompressAndSendChunkTask(new ChunkIdStruct(chunk, id)));
			}
			else if(!chunk.worldObj.chunkRoundExists(chunk.xPosition, chunk.zPosition, WorldConstants.GENCHUNK_PRELOAD_RADIUS))
			{
				((WorldServer)chunk.worldObj).theChunkProviderServer.loadAsyncWithRadius(chunk.xPosition, chunk.zPosition, WorldConstants.GENCHUNK_PRELOAD_RADIUS, this);
			}
			else //impossible?
			{
				log.fatal("Chunk[{}]({}, {}) not populated when loaded {} chunk radius", chunk.worldObj.provider.dimensionId, chunk.xPosition, chunk.zPosition,
						WorldConstants.GENCHUNK_PRELOAD_RADIUS);
				sendingQueueSize.decrementAndGet();
				sending.remove(key);
			}
		}
	};
	
	private class CompressAndSendChunkTask implements Runnable
	{
		private final ChunkIdStruct chunkId;
		private final ChunkSnapshot chunkSnapshot;
		private final Object antiXRayParam;

		public CompressAndSendChunkTask(ChunkIdStruct chunkId)
		{
			this.chunkId = chunkId;
			this.chunkSnapshot = ChunkSnapshot.of(chunkId.chunk); // must be sync
			this.antiXRayParam = antiXRayService.prepareChunkSync(this.chunkSnapshot, chunkId.chunk);
		}
		
		private boolean checkActual()
		{
			if(sending.get(ChunkHash.chunkToKey(chunkId.chunk.xPosition, chunkId.chunk.zPosition)) != chunkId.id)
			{
				sendingQueueSize.decrementAndGet();
				toUpdate.add(chunkId);
				return false;
			}
			
			return true;
		}
		
		@Override
		public void run()
		{
			if(!checkActual())
			{
				chunkSnapshot.release();
				return;
			}

			long compressionStart = System.nanoTime();

			antiXRayService.prepareChunkAsync(chunkSnapshot, antiXRayParam);
			S21PacketChunkData packet = S21PacketChunkData.makeForSend(chunkSnapshot); // may be async for chunk snapshot
			packet.deflate(); // chunkSnapshot released here

			long compressionTime = System.nanoTime() - compressionStart;
			totalCompressionTime.addAndGet(compressionTime);
			compressionCount.incrementAndGet();

			if(DEBUG)
			{
				log.log(Level.DEBUG, "[ChunkSend] Player {}: Compressed chunk ({}, {}) in {} us",
					player.getCommandSenderName(), chunkId.chunk.xPosition, chunkId.chunk.zPosition,
					compressionTime / 1000);
			}
			
			//Нужно одновременно отправить чанк и добавить его в список sendingStage2, чтобы можно было корректно отменить отправку:
			//(Если чанк есть в списке sendingStage2, посылать пакет на отгрузку. В ином случае просто удалиь из списка sending)
			synchronized(lock)
			{
				if(!checkActual())
					return;
				player.playerNetServerHandler.netManager.scheduleOutboundPacket(packet,
					new GenericFutureListener<Future<Void>>()
					{
						@Override
						public void operationComplete(Future<Void> future) throws Exception
						{
							sendingQueueSize.decrementAndGet();
						}
					});
				sendingStage2.add(ChunkHash.chunkToKey(chunkId.chunk.xPosition, chunkId.chunk.zPosition));
			}
				
			toUpdate.add(chunkId);
		}
	}
}

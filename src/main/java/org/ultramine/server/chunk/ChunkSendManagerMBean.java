package org.ultramine.server.chunk;

/**
 * JMX MBean interface for monitoring ChunkSendManager performance in real-time.
 *
 * This interface exposes key metrics and statistics about chunk sending operations
 * to external monitoring tools via JMX.
 *
 * Usage: Connect with JConsole/VisualVM to org.ultramine.server.chunk:type=ChunkSendManager
 */
public interface ChunkSendManagerMBean
{
	/**
	 * @return Player name associated with this ChunkSendManager
	 */
	String getPlayerName();

	/**
	 * @return Current chunk sending rate (chunks per tick)
	 */
	double getRate();

	/**
	 * @return Total number of chunks successfully sent to the player
	 */
	long getTotalChunksSent();

	/**
	 * @return Total number of chunks that were cancelled during sending
	 */
	long getTotalChunksCancelled();

	/**
	 * @return Total number of queue sort operations performed
	 */
	long getTotalSortOperations();

	/**
	 * @return Average chunk compression time in microseconds
	 */
	long getAverageCompressionTimeMicros();

	/**
	 * @return Current size of toSend queue (chunks waiting to be loaded)
	 */
	int getQueueToSendSize();

	/**
	 * @return Current size of sending queue (chunks being loaded/compressed)
	 */
	int getQueueSendingSize();

	/**
	 * @return Current size of sent queue (chunks already sent to player)
	 */
	int getQueueSentSize();

	/**
	 * @return Current size of network sending queue
	 */
	int getSendingQueueSize();

	/**
	 * @return View distance for this player
	 */
	int getViewDistance();

	/**
	 * @return Debug information string with all current stats
	 */
	String getDebugInfo();

	/**
	 * Reset all statistics counters to zero
	 */
	void resetStatistics();
}

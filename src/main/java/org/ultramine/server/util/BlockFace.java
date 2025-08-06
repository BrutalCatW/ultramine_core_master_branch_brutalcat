package org.ultramine.server.util;

import java.util.EnumMap;

public enum BlockFace
{
	NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH_EAST(NORTH, EAST),
    NORTH_WEST(NORTH, WEST),
    SOUTH_EAST(SOUTH, EAST),
    SOUTH_WEST(SOUTH, WEST),
    WEST_NORTH_WEST(WEST, NORTH_WEST),
    NORTH_NORTH_WEST(NORTH, NORTH_WEST),
    NORTH_NORTH_EAST(NORTH, NORTH_EAST),
    EAST_NORTH_EAST(EAST, NORTH_EAST),
    EAST_SOUTH_EAST(EAST, SOUTH_EAST),
    SOUTH_SOUTH_EAST(SOUTH, SOUTH_EAST),
    SOUTH_SOUTH_WEST(SOUTH, SOUTH_WEST),
    WEST_SOUTH_WEST(WEST, SOUTH_WEST),
    SELF(0, 0, 0);
	
	private final int modX;
	private final int modY;
	private final int modZ;

	private BlockFace(final int modX, final int modY, final int modZ)
	{
		this.modX = modX;
		this.modY = modY;
		this.modZ = modZ;
	}

	private BlockFace(final BlockFace face1, final BlockFace face2)
	{
		this.modX = face1.getModX() + face2.getModX();
		this.modY = face1.getModY() + face2.getModY();
		this.modZ = face1.getModZ() + face2.getModZ();
	}

	public int getModX()
	{
		return modX;
	}

	public int getModY()
	{
		return modY;
	}

	public int getModZ()
	{
		return modZ;
	}

	public BlockFace getOppositeFace()
	{
		// UltraMine: Mathematical optimization - use cached lookup instead of switch
		BlockFace opposite = OPPOSITES.get(this);
		return opposite != null ? opposite : BlockFace.SELF;
	}
	
	public BlockFace rotate(int notchCount)
	{
		return notchToFace(faceToNotch(this) + notchCount);
	}
	
	private static final BlockFace[] AXIS = new BlockFace[4];
	private static final BlockFace[] RADIAL = {SOUTH, SOUTH_WEST, WEST, NORTH_WEST, NORTH, NORTH_EAST, EAST, SOUTH_EAST};
	private static final EnumMap<BlockFace, Integer> NOTCHES = new EnumMap<BlockFace, Integer>(BlockFace.class);
	
	// UltraMine: Cache opposite faces for performance optimization
	private static final EnumMap<BlockFace, BlockFace> OPPOSITES = new EnumMap<BlockFace, BlockFace>(BlockFace.class);

	static
	{
		for (int i = 0; i < RADIAL.length; i++)
		{
			NOTCHES.put(RADIAL[i], i);
		}
		for (int i = 0; i < AXIS.length; i++)
		{
			AXIS[i] = RADIAL[i << 1];
		}
		
		// UltraMine: Pre-cache all opposite faces for mathematical optimization
		OPPOSITES.put(NORTH, SOUTH);
		OPPOSITES.put(SOUTH, NORTH);
		OPPOSITES.put(EAST, WEST);
		OPPOSITES.put(WEST, EAST);
		OPPOSITES.put(UP, DOWN);
		OPPOSITES.put(DOWN, UP);
		OPPOSITES.put(NORTH_EAST, SOUTH_WEST);
		OPPOSITES.put(NORTH_WEST, SOUTH_EAST);
		OPPOSITES.put(SOUTH_EAST, NORTH_WEST);
		OPPOSITES.put(SOUTH_WEST, NORTH_EAST);
		OPPOSITES.put(WEST_NORTH_WEST, EAST_SOUTH_EAST);
		OPPOSITES.put(NORTH_NORTH_WEST, SOUTH_SOUTH_EAST);
		OPPOSITES.put(NORTH_NORTH_EAST, SOUTH_SOUTH_WEST);
		OPPOSITES.put(EAST_NORTH_EAST, WEST_SOUTH_WEST);
		OPPOSITES.put(EAST_SOUTH_EAST, WEST_NORTH_WEST);
		OPPOSITES.put(SOUTH_SOUTH_EAST, NORTH_NORTH_WEST);
		OPPOSITES.put(SOUTH_SOUTH_WEST, NORTH_NORTH_EAST);
		OPPOSITES.put(WEST_SOUTH_WEST, EAST_NORTH_EAST);
		OPPOSITES.put(SELF, SELF);
	}
	
	public static BlockFace notchToFace(int notch)
	{
		return RADIAL[notch & 0x7];
	}
	
	public static int faceToNotch(BlockFace face)
	{
		return NOTCHES.get(face);
	}
	
	public static BlockFace yawToFace(float yaw)
	{
		return yawToFace(yaw, true);
	}

	public static BlockFace yawToFace(float yaw, boolean useSubCardinalDirections)
	{
		// UltraMine: Mathematical optimization - use multiplication instead of division for better performance
		if (useSubCardinalDirections)
		{
			return RADIAL[Math.round(yaw * 0.022222222f) & 0x7]; // 1/45 = 0.022222222f
		}
		else
		{
			return AXIS[Math.round(yaw * 0.011111111f) & 0x3]; // 1/90 = 0.011111111f
		}
	}
}

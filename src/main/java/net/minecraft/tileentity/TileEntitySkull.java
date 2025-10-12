package net.minecraft.tileentity;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringUtils;

public class TileEntitySkull extends TileEntity
{
	private static final ExecutorService SKIN_LOADER_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread thread = new Thread(r, "Skull Skin Loader");
		thread.setDaemon(true);
		return thread;
	});

	private int field_145908_a;
	private int field_145910_i;
	private GameProfile field_152110_j = null;
	private volatile boolean isLoadingProfile = false;
	private static final String __OBFID = "CL_00000364";

	public void writeToNBT(NBTTagCompound p_145841_1_)
	{
		super.writeToNBT(p_145841_1_);
		p_145841_1_.setByte("SkullType", (byte)(this.field_145908_a & 255));
		p_145841_1_.setByte("Rot", (byte)(this.field_145910_i & 255));

		if (this.field_152110_j != null)
		{
			NBTTagCompound nbttagcompound1 = new NBTTagCompound();
			NBTUtil.func_152460_a(nbttagcompound1, this.field_152110_j);
			p_145841_1_.setTag("Owner", nbttagcompound1);
		}
	}

	public void readFromNBT(NBTTagCompound p_145839_1_)
	{
		super.readFromNBT(p_145839_1_);
		this.field_145908_a = p_145839_1_.getByte("SkullType");
		this.field_145910_i = p_145839_1_.getByte("Rot");

		if (this.field_145908_a == 3)
		{
			if (p_145839_1_.hasKey("Owner", 10))
			{
				this.field_152110_j = NBTUtil.func_152459_a(p_145839_1_.getCompoundTag("Owner"));
			}
			else if (p_145839_1_.hasKey("ExtraType", 8) && !StringUtils.isNullOrEmpty(p_145839_1_.getString("ExtraType")))
			{
				this.field_152110_j = new GameProfile((UUID)null, p_145839_1_.getString("ExtraType"));
				this.func_152109_d();
			}
		}
	}

	public GameProfile func_152108_a()
	{
		return this.field_152110_j;
	}

	public Packet getDescriptionPacket()
	{
		NBTTagCompound nbttagcompound = new NBTTagCompound();
		this.writeToNBT(nbttagcompound);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 4, nbttagcompound);
	}

	public void func_152107_a(int p_152107_1_)
	{
		this.field_145908_a = p_152107_1_;
		this.field_152110_j = null;
	}

	public void func_152106_a(GameProfile p_152106_1_)
	{
		this.field_145908_a = 3;
		this.field_152110_j = p_152106_1_;
		this.func_152109_d();
	}

	private void func_152109_d()
	{
		if (this.field_152110_j != null && !StringUtils.isNullOrEmpty(this.field_152110_j.getName()))
		{
			if (!this.field_152110_j.isComplete() || !this.field_152110_j.getProperties().containsKey("textures"))
			{
				// Если профиль уже загружается, пропускаем
				if (isLoadingProfile)
				{
					return;
				}

				isLoadingProfile = true;

				// Асинхронная загрузка профиля игрока
				SKIN_LOADER_EXECUTOR.submit(() -> {
					try
					{
						GameProfile gameprofile = MinecraftServer.getServer().func_152358_ax().func_152655_a(this.field_152110_j.getName());

						if (gameprofile != null)
						{
							Property property = (Property)Iterables.getFirst(gameprofile.getProperties().get("textures"), (Object)null);

							if (property == null)
							{
								// Блокирующий вызов для получения текстур - выполняется в отдельном потоке
								gameprofile = MinecraftServer.getServer().func_147130_as().fillProfileProperties(gameprofile, true);
							}

							// Обновляем профиль напрямую (безопасно, так как это атомарная операция записи ссылки)
							TileEntitySkull.this.field_152110_j = gameprofile;
							// markDirty() будет вызван при следующем тике или взаимодействии с тайл-энтити
							TileEntitySkull.this.isLoadingProfile = false;
						}
						else
						{
							isLoadingProfile = false;
						}
					}
					catch (Exception e)
					{
						isLoadingProfile = false;
						// Логирование ошибки, но не прерываем работу
					}
				});
			}
		}
	}

	public int func_145904_a()
	{
		return this.field_145908_a;
	}

	public void func_145903_a(int p_145903_1_)
	{
		this.field_145910_i = p_145903_1_;
	}

	@SideOnly(Side.CLIENT)
	public int func_145906_b()
	{
		return this.field_145910_i;
	}

	@Override
	public void onChunkUnload()
	{
		super.onChunkUnload();
		// При выгрузке чанка сбрасываем флаг загрузки, чтобы избежать утечек памяти
		this.isLoadingProfile = false;
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		// При удалении тайл-энтити сбрасываем флаг загрузки
		this.isLoadingProfile = false;
	}

	/**
	 * Метод для корректной остановки ExecutorService при остановке сервера
	 * Должен быть вызван при shutdown сервера
	 */
	public static void shutdownExecutor()
	{
		SKIN_LOADER_EXECUTOR.shutdown();
		try
		{
			if (!SKIN_LOADER_EXECUTOR.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
			{
				SKIN_LOADER_EXECUTOR.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			SKIN_LOADER_EXECUTOR.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
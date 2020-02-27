package org.xjcraft.bigcoin;

import com.zjyl1994.minecraftplugin.multicurrency.services.BankService;
import com.zjyl1994.minecraftplugin.multicurrency.services.CurrencyService;
import com.zjyl1994.minecraftplugin.multicurrency.utils.OperateResult;
import com.zjyl1994.minecraftplugin.multicurrency.utils.TxTypeEnum;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.xjcraft.bigcoin.config.Config;
import org.xjcraft.bigcoin.config.ItemsConfig;
import org.xjcraft.bigcoin.config.MessageConfig;
import org.xjcraft.bigcoin.config.MinersConfig;
import org.xjcraft.utils.MathUtil;
import org.xjcraft.utils.StringUtil;

import java.math.BigDecimal;
import java.util.*;

public class BigCoinManager {
    private BigCoin plugin;
    private Timer timer = new Timer();
    Checker checker;
    int boost = 0;
    Material[] quest = null;

    public BigCoinManager(BigCoin plugin) {

        this.plugin = plugin;
        checker = new Checker();
        timer.schedule(checker, 1000L, 1000L * 60L);
    }

    public void checker() {
        if (quest != null) {
            World world = plugin.getServer().getWorld(Config.config.getWorld());
            if (world == null) {
                plugin.getLogger().warning("需要先配置世界！");
                return;
            }

            List<String> winners = getWinners(world);
            if (winners.size() > 0) {
                boost++;
                double v = Config.config.getBase() * ((1 + Math.min(boost, Config.config.getMaxBoost())) * Config.config.getBoost());
                plugin.getServer().broadcastMessage(StringUtil.applyPlaceHolder(MessageConfig.config.getWinners(), new HashMap<String, String>() {{
                    put("people", winners.size() + "");
                    put("amount", String.format("%.2f", v));
                }}));
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, ()->{
                    BigDecimal price = new BigDecimal(v);
                    OperateResult result = CurrencyService.reserveIncr(Config.config.getCurrency(), price, Config.config.getOwner());
                    if (!result.getSuccess()){
                        plugin.getLogger().warning("fail to increase reserve because:"+result.getReason());
                        return;
                    }
                    price = price.divide(new BigDecimal(winners.size()));
                    for (String winner : winners) {
                        result = BankService.transferTo("$" + Config.config.getCurrency(), winner, Config.config.getCurrency(), price, TxTypeEnum.ELECTRONIC_TRANSFER_OUT, "BigCoin");
                        if (!result.getSuccess()){
                            plugin.getLogger().warning("fail to transfer because:"+result.getReason());
                            return;
                        }
                    }

                });
            } else {
                boost--;
                if (boost < 0) boost = 0;
            }

            plugin.getServer().getScheduler().runTask(plugin, this::newQuest);
        } else {
            if (ItemsConfig.config.getItems().size() == 0) {
                for (Material value : Material.values()) {
                    if (value.getMaxStackSize() > 1) {
                        if (value.isItem())
                            ItemsConfig.config.getItems().add(value.name());
                    }
                }
                plugin.saveConfig(ItemsConfig.class);
            }
            plugin.getServer().getScheduler().runTask(plugin, this::newQuest);

        }


    }

    public List<String> getWinners(World world) {
        List<String> winners = new ArrayList<>();
        Chunk[] loadedChunks = world.getLoadedChunks();
        for (Chunk chunk : loadedChunks) {
            Map<String, String> map = MinersConfig.config.getHoppers().get(String.format("%s,%s", chunk.getX(), chunk.getZ()));
            if (map == null) continue;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                try {
                    String position = entry.getKey();
                    String[] split = position.split(",");
                    Block block = world.getBlockAt(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                    if (block.getState() instanceof Hopper) {
                        Inventory inventory = ((Hopper) block.getState()).getInventory();
                        ItemStack[] contents = inventory.getContents();
                        if (isFinished(contents)) {
                            winners.add(entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
        return winners;
    }

    public boolean isFinished(ItemStack[] contents) {
        for (int i = 0; i < 5; i++) {
            ItemStack content = contents[i];
            if (content == null || content.getType() != quest[i] || content.getMaxStackSize() != content.getAmount()) {
                return false;
            }
        }
        return true;
    }

    public void newQuest() {
        try {
            quest = new Material[5];
            String name = "";
            for (int i = 0; i < 5; i++) {
                quest[i] = Material.valueOf(ItemsConfig.config.getItems().get(MathUtil.random(0, ItemsConfig.config.getItems().size() - 1)));
                name += quest[i].name();
                name += ";";
            }
            plugin.getLogger().info("generate new quest:" + name);
            int[] needs = new int[5];
            for (int i = 0; i < 5; i++) {
                if (i > boost) {
                    needs[i] = 0;
                } else {
                    needs[i] = MathUtil.random(1, Config.config.getMaxItem());
                }
            }
            World world = plugin.getServer().getWorld(Config.config.getWorld());
            if (world == null) {
                plugin.getLogger().warning("需要先配置世界！");
                return;
            }

            Chunk[] loadedChunks = world.getLoadedChunks();
            for (Chunk chunk : loadedChunks) {
                Map<String, String> map = MinersConfig.config.getHoppers().get(String.format("%s,%s", chunk.getX(), chunk.getZ()));
                if (map == null) continue;
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    try {
                        String position = entry.getKey();
                        String[] split = position.split(",");
                        Block block = world.getBlockAt(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                        if (block.getState() instanceof Hopper) {
                            Inventory inventory = ((Hopper) block.getState()).getInventory();
                            for (int i = 0; i < 5; i++) {
                                inventory.setItem(i, new ItemStack(quest[i], quest[i].getMaxStackSize() - needs[i]));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean registerMiner(Location block, Player player) {
        Chunk chunk = block.getChunk();
        Map<String, String> map = MinersConfig.config.getHoppers().computeIfAbsent(String.format("%s,%s", chunk.getX(), chunk.getZ()), k -> new HashMap<>());
        String positon = String.format("%s,%s,%s", block.getBlockX(), block.getBlockY(), block.getBlockZ());
        String s = map.get(positon);
        if (s != null) return false;
        String put = map.put(positon, player.getName());
        plugin.saveConfig(MinersConfig.class);
        return true;
    }

    public String getMinerOwner(Location block) {
        Chunk chunk = block.getChunk();
        Map<String, String> map = MinersConfig.config.getHoppers().computeIfAbsent(String.format("%s,%s", chunk.getX(), chunk.getZ()), k -> new HashMap<>());
        String positon = String.format("%s,%s,%s", block.getBlockX(), block.getBlockY(), block.getBlockZ());
        return map.get(positon);

    }

    public boolean destroyMiner(Location block) {
        Chunk chunk = block.getChunk();
        Map<String, String> map = MinersConfig.config.getHoppers().computeIfAbsent(String.format("%s,%s", chunk.getX(), chunk.getZ()), k -> new HashMap<>());
        String positon = String.format("%s,%s,%s", block.getBlockX(), block.getBlockY(), block.getBlockZ());
        String remove = map.remove(positon);
        if (remove == null) return false;
        plugin.saveConfig(MinersConfig.class);
        return true;

    }

    class Checker extends TimerTask {
        private int count = 0;

        @Override
        public void run() {
            if (count == 0) {
                plugin.getServer().broadcastMessage(MessageConfig.config.getTimeOver());
                count = Config.config.getPeriod();
                plugin.getServer().getScheduler().runTask(plugin, BigCoinManager.this::checker);
            } else if (count < 5) {
                plugin.getServer().broadcastMessage(StringUtil.applyPlaceHolder(MessageConfig.config.getTimeLeft(), new HashMap<String, String>() {{
                    put("count", count + "");
                }}));
            }
            count--;
        }
    }
}

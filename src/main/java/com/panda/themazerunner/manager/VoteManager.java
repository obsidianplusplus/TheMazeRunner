package com.panda.themazerunner.manager;

import com.panda.themazerunner.TheMazeRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.*;

/**
 * 投票管理器
 * <p>
 * 负责处理游戏中的投票和审判逻辑，包括：
 * <ul>
 * <li>发起投票</li>
 * <li>管理投票界面 (GUI)</li>
 * <li>统计票数</li>
 * <li>执行审判 (流放/宽恕)</li>
 * </ul>
 */
public class VoteManager {

    private final TheMazeRunner plugin;
    /**
     * 投票记录: 投票者 UUID -> 候选人 UUID (null 表示弃权)
     */
    private final Map<UUID, UUID> votes = new HashMap<>(); // Voter -> Candidate (null for abstain)
    /**
     * 审判投票记录: 投票者 UUID -> 是否流放 (true=流放, false=宽恕)
     */
    private final Map<UUID, Boolean> judgmentVotes = new HashMap<>(); // Voter -> Exile? (true=exile, false=forgive)

    private boolean isVotingInProgress = false;
    private boolean isTrialInProgress = false;
    private Player currentAccused = null;
    /**
     * 记录已收到平票警告的玩家以及他们选择的方向 (true=Exile, false=Forgive)
     */
    private final Map<UUID, Boolean> tremblingHandChoices = new HashMap<>(); // Tracks players warned about tie

    public VoteManager(TheMazeRunner plugin) {
        this.plugin = plugin;
    }

    // 投票阶段

    /**
     * 开始投票阶段并分发投票券。
     *
     * @param participants 参与投票的玩家列表
     */
    public void startVoting(List<Player> participants) {
        isVotingInProgress = true;
        votes.clear();

        ItemStack ticket = new ItemStack(Material.PAPER);
        ItemMeta meta = ticket.getItemMeta();

        // 使用 Adventure API 设置显示名称
        meta.displayName(plugin.getLanguageManager().getComponent("vote.ticket.name")
                .decoration(TextDecoration.ITALIC, false));

        // 使用 Adventure API 设置 Lore
        meta.lore(Collections.singletonList(
                plugin.getLanguageManager().getComponent("vote.ticket.lore")
                        .decoration(TextDecoration.ITALIC, false)));
        ticket.setItemMeta(meta);

        for (Player p : participants) {
            p.getInventory().addItem(ticket);
            p.sendMessage(plugin.getLanguageManager().getComponent("vote.received"));
        }
    }

    /**
     * 为指定玩家打开投票 GUI。
     *
     * @param voter 投票者
     */
    public void openVoteGui(Player voter) {
        if (!isVotingInProgress) {
            voter.sendMessage(plugin.getLanguageManager().getComponent("vote.not_in_progress"));
            return;
        }

        // 假设会议参与者即为所有在线玩家，或者从 AreaManager 获取
        // 这里暂时使用在线玩家以保持稳健性，后续应从 AreaManager 获取参与者列表
        List<Player> candidates = new ArrayList<>(plugin.getAreaManager().getMeetingParticipants());
        if (candidates.isEmpty()) {
            candidates.addAll(Bukkit.getOnlinePlayers());
        }

        Inventory gui = Bukkit.createInventory(null, 54,
                plugin.getLanguageManager().getComponent("vote.gui_title"));

        // 添加候选人头像
        int index = 0;
        for (Player p : candidates) {
            if (index >= 45)
                break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.displayName(Component.text(p.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(Collections.singletonList(
                    plugin.getLanguageManager().getComponent("vote.candidate_lore")
                            .decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
            gui.setItem(index++, head);
        }

        // 添加弃权按钮 (Barrier)
        ItemStack abstain = new ItemStack(Material.BARRIER);
        ItemMeta abMeta = abstain.getItemMeta();
        abMeta.displayName(plugin.getLanguageManager().getComponent("vote.abstain_name")
                .decoration(TextDecoration.ITALIC, false));
        abstain.setItemMeta(abMeta);
        gui.setItem(49, abstain);

        voter.openInventory(gui);
    }

    /**
     * 玩家进行投票。
     *
     * @param voter       投票者
     * @param candidateId 候选人 UUID (null 为弃权)
     */
    public void castVote(Player voter, UUID candidateId) {
        votes.put(voter.getUniqueId(), candidateId); // null 表示弃权
        voter.sendMessage(plugin.getLanguageManager().getComponent("vote.success"));
        voter.closeInventory();

        // 移除一张投票券
        for (ItemStack item : voter.getInventory().getContents()) {
            if (item != null && item.getType() == Material.PAPER && item.hasItemMeta()) {
                // 使用 Adventure API 检查物品名
                Component displayName = item.getItemMeta().displayName();
                // 简单检查是否包含特定文本 (这里可以使用更严格的序列化检查或 PDC)
                // TODO: 使用 PersistentDataContainer 进行更安全的物品识别
                if (displayName != null) {
                    item.setAmount(item.getAmount() - 1);
                    break;
                }
            }
        }
    }

    /**
     * 计算被流放的候选人。
     *
     * @return 票数最高的玩家，如果是平票或无人得票则返回 null
     */
    public Player calculateExileCandidate() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID cand : votes.values()) {
            if (cand == null)
                continue; // 弃权
            counts.put(cand, counts.getOrDefault(cand, 0) + 1);
        }

        if (counts.isEmpty())
            return null;

        UUID maxUuid = null;
        int maxVotes = -1;
        boolean tie = false;

        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                maxUuid = entry.getKey();
                tie = false;
            } else if (entry.getValue() == maxVotes) {
                tie = true;
            }
        }

        if (tie || maxUuid == null)
            return null; // 平票或无票 -> 不流放

        return Bukkit.getPlayer(maxUuid);
    }

    /**
     * 结束投票阶段。
     */
    public void endVoting() {
        isVotingInProgress = false;
        // 可以在这里处理清理票据的逻辑
    }

    // 审判阶段 (Judgment Phase)

    /**
     * 开始审判阶段。
     *
     * @param accused 被告玩家
     */
    public void startJudgment(Player accused) {
        this.currentAccused = accused;
        this.isTrialInProgress = true;
        this.judgmentVotes.clear();
        this.judgmentVotes.clear();
        this.tremblingHandChoices.clear(); // 重置警告记录

        LanguageManager lm = plugin.getLanguageManager();

        // 广播消息
        Bukkit.broadcast(lm.getComponent("judgment.start_header"));
        Bukkit.broadcast(lm.getComponent("judgment.accused")
                .replaceText(b -> b.match("%player%").replacement(accused.getName())));
        Bukkit.broadcast(lm.getComponent("judgment.instruction"));

        // 强制为所有在线玩家打开审判 GUI
        for (Player p : Bukkit.getOnlinePlayers()) {
            openJudgmentGui(p);
        }
    }

    /**
     * 打开审判 GUI。
     *
     * @param voter 投票者
     */
    public void openJudgmentGui(Player voter) {
        if (!isTrialInProgress || currentAccused == null)
            return;

        LanguageManager lm = plugin.getLanguageManager();

        Inventory gui = Bukkit.createInventory(null, 27,
                lm.getComponent("judgment.gui_title")
                        .replaceText(b -> b.match("%player%").replacement(currentAccused.getName())));

        ItemStack forgive = new ItemStack(Material.LIME_WOOL);
        ItemMeta fMeta = forgive.getItemMeta();
        fMeta.displayName(
                lm.getComponent("judgment.forgive_name").decoration(TextDecoration.ITALIC, false));
        forgive.setItemMeta(fMeta);

        ItemStack exile = new ItemStack(Material.RED_WOOL);
        ItemMeta eMeta = exile.getItemMeta();
        eMeta.displayName(
                lm.getComponent("judgment.exile_name").decoration(TextDecoration.ITALIC, false));
        exile.setItemMeta(eMeta);

        gui.setItem(11, forgive);
        gui.setItem(15, exile);

        // 如果玩家之前触发了"颤抖的手"，恢复那个选项为不详之瓶
        if (tremblingHandChoices.containsKey(voter.getUniqueId())) {
            boolean isExileChoice = tremblingHandChoices.get(voter.getUniqueId());

            ItemStack ominous = new ItemStack(Material.OMINOUS_BOTTLE);
            ItemMeta meta = ominous.getItemMeta();
            meta.displayName(plugin.getLanguageManager().getComponent("vote.trembling.item_name")
                    .decoration(TextDecoration.BOLD, true));

            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            ominous.setItemMeta(meta);

            if (isExileChoice) {
                gui.setItem(15, ominous);
            } else {
                gui.setItem(11, ominous);
            }
        }

        voter.openInventory(gui);
    }

    /**
     * 玩家进行审判投票。
     *
     * @param voter 投票者
     * @param exile true 为流放，false 为宽恕
     */
    public void castJudgment(Player voter, boolean exile) {
        judgmentVotes.put(voter.getUniqueId(), exile);
        LanguageManager lm = plugin.getLanguageManager();
        voter.sendMessage(exile ? lm.getComponent("judgment.voted_exile")
                : lm.getComponent("judgment.voted_forgive"));
        voter.closeInventory();
    }

    /**
     * 评估审判结果。
     *
     * @return 审判结果 (流放、宽恕或惩罚)
     */
    public JudgmentResult evaluateJudgment() {
        int forgive = 0;
        int exile = 0;

        for (Boolean isExile : judgmentVotes.values()) {
            if (isExile)
                exile++;
            else
                forgive++;
        }

        LanguageManager lm = plugin.getLanguageManager();

        final int finalForgive = forgive;
        final int finalExile = exile;

        Bukkit.broadcast(lm.getComponent("judgment.result_header")
                .append(lm.getComponent("judgment.result_forgive")
                        .replaceText(b -> b.match("%count%").replacement(String.valueOf(finalForgive))))
                .append(Component.text(", ", NamedTextColor.GOLD))
                .append(lm.getComponent("judgment.result_exile")
                        .replaceText(b -> b.match("%count%").replacement(String.valueOf(finalExile)))));

        if (exile > forgive)
            return JudgmentResult.EXILE;
        if (forgive > exile)
            return JudgmentResult.FORGIVE;
        return JudgmentResult.PUNISHMENT;
    }

    /**
     * 结束审判。
     */
    public void endTrial() {
        isTrialInProgress = false;
        currentAccused = null;
    }

    /**
     * 审判结果枚举。
     */
    public enum JudgmentResult {
        /**
         * 宽恕
         */
        FORGIVE,
        /**
         * 流放
         */
        EXILE,
        /**
         * 惩罚 (如平票或其他情况)
         */
        PUNISHMENT
    }

    /**
     * @return 是否正在进行投票
     */
    public boolean isVotingInProgress() {
        return isVotingInProgress;
    }

    /**
     * @return 是否正在进行审判
     */
    public boolean isTrialInProgress() {
        return isTrialInProgress;
    }

    /**
     * @return 当前被告
     */
    public Player getCurrentAccused() {
        return currentAccused;
    }

    /**
     * 检查玩家是否已收到"颤抖的手"警告。
     *
     * @param player 玩家
     * @return 是否已警告
     */
    public boolean hasWarnedTremblingHand(Player player) {
        return tremblingHandChoices.containsKey(player.getUniqueId());
    }

    /**
     * 设置玩家已收到"颤抖的手"警告。
     *
     * @param player 玩家
     * @param choice true=exile, false=forgive
     */
    public void setWarnedTremblingHand(Player player, boolean choice) {
        tremblingHandChoices.put(player.getUniqueId(), choice);
    }

    /**
     * 检查当前的一票是否会导致平局。
     * 仅在这是最后一票时有效。
     *
     * @param isExileVote 这一票是否是流放票
     * @return 是否会导致平局
     */
    public boolean wouldCauseTie(boolean isExileVote) {
        int totalParticipants = plugin.getAreaManager().getMeetingParticipants().size(); // 需要准确的参与人数
        if (totalParticipants <= 0)
            return false;

        int currentVoteCount = judgmentVotes.size();

        // 检查是否是最后一票 (当前已投 + 这一票 == 总人数)
        if (currentVoteCount + 1 != totalParticipants) {
            return false;
        }

        // 模拟结果
        int forgive = 0;
        int exile = 0;

        for (Boolean vote : judgmentVotes.values()) {
            if (vote)
                exile++;
            else
                forgive++;
        }

        if (isExileVote)
            exile++;
        else
            forgive++;

        return exile == forgive;
    }

    /**
     * 检查玩家是否已经在当前的审判中投票。
     *
     * @param player 玩家
     * @return 是否已投票
     */
    public boolean hasVotedInJudgment(Player player) {
        return judgmentVotes.containsKey(player.getUniqueId());
    }
}

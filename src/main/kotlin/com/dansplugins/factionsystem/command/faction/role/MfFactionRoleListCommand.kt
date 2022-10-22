package com.dansplugins.factionsystem.command.faction.role

import com.dansplugins.factionsystem.MedievalFactions
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission.Companion.CREATE_ROLE
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission.Companion.DELETE_ROLE
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission.Companion.MODIFY_ROLE
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission.Companion.SET_DEFAULT_ROLE
import com.dansplugins.factionsystem.faction.permission.MfFactionPermission.Companion.SET_MEMBER_ROLE
import com.dansplugins.factionsystem.pagination.PaginatedView
import com.dansplugins.factionsystem.player.MfPlayer
import dev.forkhandles.result4k.onFailure
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.logging.Level.SEVERE
import net.md_5.bungee.api.ChatColor as SpigotChatColor
import org.bukkit.ChatColor as BukkitChatColor

class MfFactionRoleListCommand(private val plugin: MedievalFactions) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("mf.role.list")) {
            sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListNoPermission"]}")
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListNotAPlayer"]}")
            return true
        }
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val playerService = plugin.services.playerService
            val mfPlayer = playerService.getPlayer(sender)
                ?: playerService.save(MfPlayer(plugin, sender)).onFailure {
                    sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListFailedToSavePlayer"]}")
                    plugin.logger.log(SEVERE, "Failed to save player: ${it.reason.message}", it.reason.cause)
                    return@Runnable
                }
            val factionService = plugin.services.factionService
            val faction = factionService.getFaction(mfPlayer.id)
            if (faction == null) {
                sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListMustBeInAFaction"]}")
                return@Runnable
            }
            val playerRole = faction.getRole(mfPlayer.id)
            if (playerRole == null || !playerRole.hasPermission(faction, MfFactionPermission.LIST_ROLES)) {
                sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListNoFactionPermission"]}")
                return@Runnable
            }
            val pageNumber = args.lastOrNull()?.toIntOrNull()?.minus(1) ?: 0
            val view = PaginatedView(
                plugin.language,
                lazy {
                    arrayOf(
                        TextComponent(plugin.language["CommandFactionRoleListTitle"]).apply {
                            color = SpigotChatColor.AQUA
                            isBold = true
                        }
                    )
                },
                faction.roles.map { role ->
                    lazy {
                        buildList {
                            add(TextComponent(
                                plugin.language["CommandFactionRoleListItem", role.name]
                            ).apply {
                                color = SpigotChatColor.GRAY
                                clickEvent = ClickEvent(RUN_COMMAND, "/faction role view ${role.id.value}")
                                hoverEvent = HoverEvent(
                                    SHOW_TEXT,
                                    Text(plugin.language["CommandFactionRoleListItemHover", role.name])
                                )
                            })
                            if (faction.roles.defaultRoleId == role.id) {
                                add(TextComponent(" "))
                                add(TextComponent(
                                    plugin.language["CommandFactionRoleListDefault"]
                                ).apply {
                                    color = SpigotChatColor.AQUA
                                })
                            }
                            if (playerRole.hasPermission(faction, MODIFY_ROLE(role.id))) {
                                add(TextComponent(" "))
                                add(TextComponent(
                                    plugin.language["CommandFactionRoleListRenameButton", role.name]
                                ).apply {
                                    color = SpigotChatColor.GREEN
                                    clickEvent = ClickEvent(RUN_COMMAND, "/faction role rename ${role.id.value} p=${pageNumber + 1}")
                                    hoverEvent = HoverEvent(
                                        SHOW_TEXT,
                                        Text(plugin.language["CommandFactionRoleListRenameButtonHover", role.name])
                                    )
                                })
                            }
                            if (playerRole.hasPermission(faction, DELETE_ROLE(role.id))) {
                                add(TextComponent(" "))
                                add(TextComponent(
                                    plugin.language["CommandFactionRoleListDeleteButton", role.name]
                                ).apply {
                                    color = SpigotChatColor.RED
                                    clickEvent = ClickEvent(RUN_COMMAND, "/faction role delete ${role.id.value} p=${pageNumber + 1}")
                                    hoverEvent = HoverEvent(
                                        SHOW_TEXT,
                                        Text(plugin.language["CommandFactionRoleListDeleteButtonHover", role.name])
                                    )
                                })
                            }
                            if (playerRole.hasPermission(faction, SET_DEFAULT_ROLE) && playerRole.hasPermission(faction, SET_MEMBER_ROLE(role.id))) {
                                add(TextComponent(" "))
                                add(TextComponent(
                                    plugin.language["CommandFactionRoleListSetDefaultRoleButton"]
                                ).apply {
                                    color = SpigotChatColor.YELLOW
                                    clickEvent = ClickEvent(RUN_COMMAND, "/faction role setdefault ${role.id.value} p=${pageNumber + 1}")
                                    hoverEvent = HoverEvent(
                                        SHOW_TEXT,
                                        Text(plugin.language["CommandFactionRoleListSetDefaultRoleButtonHover", role.name])
                                    )
                                })
                            }
                        }.toTypedArray()
                    }
                }
            ) { page -> "/faction role list ${page + 1}" }
            if (pageNumber !in view.pages.indices) {
                sender.sendMessage("${BukkitChatColor.RED}${plugin.language["CommandFactionRoleListInvalidPageNumber"]}")
                return@Runnable
            }
            view.sendPage(sender, pageNumber)
            if (playerRole.hasPermission(faction, CREATE_ROLE)) {
                sender.spigot().sendMessage(*arrayOf(
                    TextComponent(
                        plugin.language["CommandFactionRoleListCreateButton"]
                    ).apply {
                        color = SpigotChatColor.GREEN
                        clickEvent = ClickEvent(RUN_COMMAND, "/faction role create")
                        hoverEvent = HoverEvent(SHOW_TEXT, Text(plugin.language["CommandFactionRoleListCreateButtonHover"]))
                    }
                ))
            }
        })
        return true
    }

}
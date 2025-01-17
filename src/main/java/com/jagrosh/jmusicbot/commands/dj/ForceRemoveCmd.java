/*
 * Copyright 2019 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.commons.utils.FinderUtil;
import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.DJCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Michaili K.
 */
public class ForceRemoveCmd extends DJCommand {

  public ForceRemoveCmd(Bot bot) {
    super(bot);
    this.name = "forceremove";
    this.help = "убирает все пластинки от пользователя";
    this.arguments = "<user>";
    this.options = Collections.singletonList(new OptionData(OptionType.STRING, "user", "Пользователь, очередь которого нужно убрать.").setRequired(true));
    this.aliases = bot.getConfig().getAliases(this.name);
    this.beListening = false;
    this.bePlaying = true;
    this.botPermissions = new Permission[] { Permission.MESSAGE_EMBED_LINKS };
  }

  @Override
  public void doCommand(CommandEvent event) {
    if (event.getArgs().isEmpty()) {
      event.replyError("Вам нужно выбрать пользователя!");
      return;
    }

    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
    if (handler.getQueue().isEmpty()) {
      event.replyError("В очереди ничего нет!");
      return;
    }

    User target;
    List<Member> found = FinderUtil.findMembers(event.getArgs(), event.getGuild());

    if (found.isEmpty()) {
      event.replyError("Не удалось найти пользователя!");
      return;
    } else if (found.size() > 1) {
      OrderedMenu.Builder builder = new OrderedMenu.Builder();
      for (int i = 0; i < found.size() && i < 4; i++) {
        Member member = found.get(i);
        builder.addChoice("**" + member.getUser().getName() + "**#" + member.getUser().getDiscriminator());
      }

      builder.setSelection((msg, i) -> removeAllEntries(found.get(i - 1).getUser(), event))
        .setText("Найдено несколько пользователей:")
        .setColor(event.getSelfMember().getColor())
        .useNumbers()
        .setUsers(event.getAuthor())
        .useCancelButton(true)
        .setCancel(msg -> {})
        .setEventWaiter(bot.getWaiter())
        .setTimeout(1, TimeUnit.MINUTES)
        .build()
        .display(event.getChannel());
      return;
    } else {
      target = found.get(0).getUser();
    }

    removeAllEntries(target, event);
  }

  @Override
  public void doSlashCommand(SlashCommandEvent event) {
    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
    if (handler.getQueue().isEmpty()) {
      event.getHook().editOriginal("В очереди ничего нет!")
        .queue();
      return;
    }

    User target;
    List<Member> found = FinderUtil.findMembers(event.getOption("user").getAsString(), event.getGuild());

    if (found.isEmpty()) {
      event.getHook().editOriginal("Не удалось найти пользователя!")
        .queue();
      return;
    } else if (found.size() > 1) {
      StringBuilder sb = new StringBuilder();
      ItemComponent[] components = new ItemComponent[found.size() + 1];
      sb.append("Найдено несколько пользователей: \n");
      for (int i = 0; i < found.size() && i < 4; i++) {
        sb.append((i) + " **" + found.get(i).getUser().getName() + "**#" + found.get(i).getUser().getDiscriminator() + "\n");
        components[i] = Button.primary(String.valueOf(i), String.valueOf(i));
      }
      components[found.size()] = Button.danger(String.valueOf(found.size()), "Cancel");
      event.getHook().editOriginal(sb.toString()).setActionRow(components).queue();

      bot.getWaiter().waitForEvent(ButtonInteractionEvent.class, (e) -> true, e -> {
        int buttonID = Integer.parseInt(e.getInteraction().getComponentId());
        if(buttonID == found.size()) {
          e.editMessage("Отменено!").setActionRow()
            .delay(5, TimeUnit.SECONDS).flatMap(InteractionHook::deleteOriginal).queue();
        } else {
          removeAllEntriesSlash(found.get(buttonID).getUser(), event);
        }
      });

      return;
    } else {
      target = found.get(0).getUser();
    }

    removeAllEntriesSlash(target, event);
  }


  private void removeAllEntries(User target, CommandEvent event) {
    int count = ((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).getQueue().removeAll(target.getIdLong());
    if (count == 0) {
      event.replyWarning("Пользователь с ником **" + target.getName() + "** не добавлял пластинки в очередь!");
    } else {
      event.replySuccess("Успешно убраны пластинки пользователя **" + target.getName() + "**#" + target.getDiscriminator() + ".");
    }
  }

  private void removeAllEntriesSlash(User target, SlashCommandEvent event) {
    int count = ((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).getQueue().removeAll(target.getIdLong());
    if (count == 0) {
      event.getHook().editOriginal("Пользователь с ником **" + target.getName() + "** не добавлял пластинки в очередь!").setActionRow()
        .queue();
    } else {
      event.getHook().editOriginal("Успешно убраны пластинки пользователя **" + target.getName() + "**#" + target.getDiscriminator() + ".").setActionRow()
        .queue();
    }
  }
}

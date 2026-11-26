package ru.privatenull.pnrelog.integration;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** {@code %pnrelog_opponents_contains_<ник>_formatted%}: тот же ответ в виде из настроек. */
@Placeholders("opponents")
public final class OpponentsFormat {
    private final PnRelogPlaceholders values;

    public OpponentsFormat(PnRelogPlaceholders values) {
        this.values = values;
    }

    @Placeholder
    public Reply<Formatted> contains(@Viewer ViewerInfo viewer, @Argument ArgumentValue<String> name) {
        boolean has = viewer.getPlayerId() != null && values.hasOpponent(viewer.getPlayerId(), name.required());
        return Reply.next(new Formatted(values.yesNo(has)));
    }
}
